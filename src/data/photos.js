// 稳定的数据适配层，不随发布改动。
// 照片分组、顺序、日期、图注、宽高比全部由管理台「04 发布」写入 content/photos.json。
// 这里只保留派生逻辑（平铺列表、编号），属于代码而非内容。
import data from '../../content/photos.json'

export const heroPhoto = data.heroPhoto
export const photoGroups = data.photoGroups
export const dims = data.dims

// 全部照片平铺（Lightbox 翻页用，按分组顺序）
export const flatPlates = photoGroups.flatMap((g) => g.plates)

export const plateNo = (idx) => `PLATE.${String(idx + 1).padStart(3, '0')}`
