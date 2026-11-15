/**
 * runWithTools（流式版）—— 语义源自 @cloudflare/ai-utils@1.0.1（MIT，src/runWithTools.ts）
 * 与官方 provider 的流式解析约定（cloudflare/ai packages/workers-ai-provider streaming.ts +
 * 官方 issue #2482 / PR #663），按本仓库零依赖风格手写。与官方 runWithTools 的差异：
 *   ① 循环轮也 stream:true —— 无工具调用的常见对话保持全程流式（官方版循环轮固定非流式）
 *   ② 流式 tool_calls 聚合：llama-3.3 会把同一参数片段在顶层 tool_calls 与
 *      choices[0].delta.tool_calls 双份上报（issue #2482），按官方 PR #663 规则只取 delta 副本
 *   ③ 工具结果回填沿用 Workers AI 原生约定：assistant 轮 = JSON.stringify(toolCall)，
 *      结果 = {role:"tool", content:JSON.stringify(result), name}；无 tool_call_id 配对
 *   ④ 并行调用：先按序回填全部 assistant echo，再按序回填全部结果（比官方逐对交错更连贯）
 *   ⑤ 新增 onToolEvent / callOptions；去掉 Logger / zod / trim
 *   ⑥ 递归轮数用尽后的最终调用不带 tools（保证最终流式响应不会再被 tool_calls 打断）
 */

/**
 * @param {{run: Function}} ai Workers AI binding
 * @param {string} model
 * @param {{messages: Array, tools: Array<{name:string, description:string, parameters:object, function:Function}>}} input
 * @param {{maxRecursiveToolRuns?: number, onToolEvent?: Function, callOptions?: object,
 *          emitDelta?: Function}} config
 *   emitDelta(text) —— 每个内容增量回调（保持全程流式；无工具轮即最终回答本身）
 * @returns {Promise<{toolRounds: number}>} 工具轮数（调用方记录/调试用）
 */
export async function runWithTools(ai, model, input, config = {}) {
  const { maxRecursiveToolRuns = 0, onToolEvent = null, callOptions = {}, emitDelta = null } = config
  const messages = [...input.messages]

  /** 单轮流式解析：内容增量实时回调；tool_calls 按 index 聚合
   *  防重是流级而非帧级（2026-11-15 自测发现）：llama-3.3 的双份上报跨帧出现——
   *  delta 副本帧之后可能跟只带顶层扁形副本的帧，按帧内择一会把片段拼两遍导致
   *  JSON.parse 失败。规则：本流一旦出现过 delta 副本，忽略全部顶层帧。 */
  async function consumeToolStream(stream) {
    const reader = stream.getReader()
    const dec = new TextDecoder()
    let buf = ""
    let sawDeltaToolCalls = false
    const calls = new Map() // index -> {id, name, args}
    const take = (tc) => {
      const fn = tc.function || {}
      const argFragment = typeof fn.arguments === "string" ? fn.arguments
        : typeof tc.arguments === "string" ? tc.arguments
        : fn.arguments != null ? JSON.stringify(fn.arguments)
        : tc.arguments != null ? JSON.stringify(tc.arguments) : ""
      const idx = tc.index ?? 0
      const cur = calls.get(idx) || { id: null, name: "", args: "" }
      if (tc.id) cur.id = tc.id
      if (fn.name || tc.name) cur.name += fn.name || tc.name || ""
      if (argFragment) cur.args += argFragment
      calls.set(idx, cur)
    }
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += dec.decode(value, { stream: true })
      let idx
      while ((idx = buf.indexOf("\n")) >= 0) {
        const line = buf.slice(0, idx).trim()
        buf = buf.slice(idx + 1)
        if (!line.startsWith("data:")) continue
        const payload = line.slice(5).trim()
        if (payload === "[DONE]") continue
        try {
          const j = JSON.parse(payload)
          const delta = j.choices?.[0]?.delta
          if (delta?.tool_calls?.length) {
            sawDeltaToolCalls = true
            for (const tc of delta.tool_calls) take(tc)
          } else if (!sawDeltaToolCalls && Array.isArray(j.tool_calls) && j.tool_calls.length) {
            for (const tc of j.tool_calls) take(tc) // 纯顶层格式的 provider 才走这里
          }
          const text = delta?.content ?? j.response ?? ""
          if (text && !(delta && delta.tool_calls && delta.tool_calls.length)) emitDelta?.(String(text))
        } catch {
          // 非 JSON 行忽略（分片跨行已由缓冲处理）
        }
      }
    }
    return [...calls.values()]
      .filter((c) => c.name)
      .map((c) => {
        let args = {}
        try {
          args = c.args ? JSON.parse(c.args) : {}
        } catch {
          // 参数不完整（截断）→ 空对象，工具侧按缺参处理
        }
        return { name: c.name, arguments: args }
      })
  }

  async function runAndProcessToolCall(runsLeft, toolRounds) {
    const stream = await ai.run(model, {
      messages,
      stream: true,
      tools: input.tools.map(({ name, description, parameters }) => ({
        type: "function",
        function: { name, description, parameters },
      })),
      ...callOptions,
    })
    const toolCalls = await consumeToolStream(stream)
    if (toolCalls.length === 0) return { toolRounds } // 本流即最终回答（已实时 emitDelta）

    // 工具轮：先按序回填 echo，再执行并回填结果（语义同官方，分组更连贯）
    for (const tc of toolCalls) messages.push({ role: "assistant", content: JSON.stringify(tc) })
    await Promise.all(
      toolCalls.map(async (tc) => {
        const tool = input.tools.find((t) => t.name === tc.name)
        if (!tool?.function) {
          messages.push({ role: "tool", content: `Error executing tool ${tc.name}: tool not found`, name: tc.name })
          return
        }
        try {
          onToolEvent?.({ name: tool.name, phase: "start" })
          const result = await tool.function(tc.arguments)
          messages.push({ role: "tool", content: JSON.stringify(result), name: tool.name })
        } catch (error) {
          messages.push({
            role: "tool",
            content: `Error executing tool ${tool.name}: ${error.message}`,
            name: tool.name,
          })
        } finally {
          onToolEvent?.({ name: tool.name, phase: "done" })
        }
      })
    )
    if (runsLeft > 0) return runAndProcessToolCall(runsLeft - 1, toolRounds + 1)

    // 轮数用尽：最终回答调用不带 tools，纯流式输出
    const finalStream = await ai.run(model, { messages, stream: true, ...callOptions })
    await consumeToolStream(finalStream)
    return { toolRounds: toolRounds + 1 }
  }

  return runAndProcessToolCall(maxRecursiveToolRuns, 0)
}
