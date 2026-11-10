#!/usr/bin/env python3
"""GitHub Git Data API 历史重放 v2：binary-safe（bytes 读 blob）+ 断点续传（远端 HEAD 匹配本地序列后继续）。"""
import base64, json, subprocess, time, urllib.request, urllib.error

OWNER, REPO = "yuanyyyyyyyyy", "rain"
API = f"https://api.github.com/repos/{OWNER}/{REPO}"
TOKEN = subprocess.run(["gh", "auth", "token"], capture_output=True, text=True).stdout.strip()
HDR = {"Authorization": f"Bearer {TOKEN}", "Accept": "application/vnd.github+json",
       "User-Agent": "rain-replay", "Content-Type": "application/json"}

def api(method, path, payload=None, retries=4):
    req = urllib.request.Request(f"{API}{path}", method=method,
        data=json.dumps(payload).encode() if payload is not None else None, headers=HDR)
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.loads(r.read().decode() or "{}")
        except urllib.error.HTTPError as e:
            if e.code in (429, 500, 502, 503) and attempt < retries - 1:
                time.sleep(3 * (attempt + 1)); continue
            body = e.read().decode()[:300]
            raise RuntimeError(f"{method} {path} -> {e.code}: {body}")
        except (urllib.error.URLError, TimeoutError) as e:
            if attempt < retries - 1:
                time.sleep(3 * (attempt + 1)); continue
            raise

def git_bytes(*args):
    return subprocess.run(["git", *args], capture_output=True, check=True).stdout

def git_text(*args):
    return git_bytes(*args).decode("utf-8")

def ls_tree(sha):
    cur = {}
    for line in git_text("ls-tree", "-r", sha).strip().split("\n"):
        if not line:
            continue
        meta, path = line.split("\t", 1)
        mode, typ, bsha = meta.split()
        cur[path] = (mode, bsha)
    return cur

lines = git_text("log", "--reverse", "--format=%H%x01%T%x01%an%x01%ae%x01%aI%x01%s").strip().split("\n")
# [sha, tree, an, ae, ad, msg]：tree sha 由文件内容决定，重放（内容等价）后保持一致
commits = [l.split("\x01", 5) for l in lines]

br = api("GET", "/branches/main")
parent_sha = br["commit"]["sha"]
parent_tree = br["commit"]["commit"]["tree"]["sha"]
head_msg = br["commit"]["commit"]["message"].strip()
remote_tree = parent_tree

# 断点：远端 HEAD 对应本地序列中的位置。必须「message + tree」双匹配：
# publish commit 消息固定重复，而重放通道会使本地/远端 hash 分叉——只按消息匹配会把
# 未推送的同名 publish commit 误判为已推送（2026-11-10 事故：任务 C/F 与截图静默丢失）。
start_idx = 0
prev_files = {}
for i in range(len(commits) - 1, -1, -1):  # 从最新往回：取最新一个真正等价的断点
    if commits[i][5].strip() == head_msg and commits[i][1] == remote_tree:
        start_idx = i + 1
        prev_files = ls_tree(commits[i][0])
        break
if start_idx == 0 and head_msg:
    # 并发护栏（2026-11-10）：多会话/多工作区同时发布时，远端 HEAD 可能在本地历史里
    # 找不到等价断点。此时全量重放会把线上文件树回退成本地旧状态（事故：画质修复、
    # 照片排序被静默覆盖），且发布界面仍显示成功。宁可让同步明确失败，也不重放历史。
    raise SystemExit(
        "ABORT: 远端 HEAD 在本地历史里找不到可信断点，已禁止全量重放历史。"
        " 通常是另一个会话/工作区更新过 main。请先 git fetch origin main，"
        " 确认本地基于 origin/main 之后再发布；不要用 force push 绕过。"
    )
print(f"remote HEAD: {head_msg[:40]!r} -> resume from #{start_idx + 1}/{len(commits)}", flush=True)

for i in range(start_idx, len(commits)):
    sha, ltree, an, ae, ad, msg = commits[i]
    cur = ls_tree(sha)
    changes = []
    uploaded = 0
    for path, (mode, bsha) in cur.items():
        if prev_files.get(path) != (mode, bsha):
            blob = git_bytes("cat-file", "blob", bsha)
            r = api("POST", "/git/blobs", {"content": base64.b64encode(blob).decode(), "encoding": "base64"})
            changes.append({"path": path, "mode": mode, "type": "blob", "sha": r["sha"]})
            uploaded += 1
    for path in prev_files:
        if path not in cur:
            changes.append({"path": path, "mode": prev_files[path][0], "type": "blob", "sha": None})
    tree_payload = {"tree": changes}
    if parent_tree:
        tree_payload["base_tree"] = parent_tree
    tree = api("POST", "/git/trees", tree_payload)
    commit = api("POST", "/git/commits", {
        "message": msg, "tree": tree["sha"],
        "parents": ([parent_sha] if parent_sha else []),
        "author": {"name": an, "email": ae, "date": ad},
        "committer": {"name": an, "email": ae, "date": ad}})
    api("PATCH", "/git/refs/heads/main", {"sha": commit["sha"], "force": False})
    parent_sha, parent_tree = commit["sha"], tree["sha"]
    prev_files = cur
    print(f"[{i+1}/{len(commits)}] {msg[:40]}  (+{uploaded} blobs)", flush=True)

print(f"DONE: main -> {parent_sha[:10]}")

# API ref 更新不保证触发 push event：显式触发部署 workflow
import subprocess as sp
r = sp.run(["gh", "workflow", "run", "deploy.yml", "--repo", f"{OWNER}/{REPO}"], capture_output=True, text=True)
print("deploy triggered" if r.returncode == 0 else f"deploy trigger failed: {r.stderr}")
