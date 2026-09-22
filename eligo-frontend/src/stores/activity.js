import { reactive } from "vue"
import { getActivities } from "../api/activity"
import { activityFeed } from "../mock/events"

/**
 * 活动全局状态管理
 * 用于跨页面共享活动数据（广场、地图、详情之间的缓存）
 */
const state = reactive({
  /** 活动列表 */
  list: [],
  /** 当前选中的 tab 分类 */
  category: "关注",
  /** 是否正在加载 */
  loading: false,
  /** 当前页码 */
  page: 1,
  /** 是否还有更多 */
  hasMore: true,
})

/**
 * 加载活动列表
 * @param {boolean} reset - 是否重置（下拉刷新/切换分类）
 */
async function fetchList(reset = false) {
  if (state.loading) return
  state.loading = true

  if (reset) {
    state.page = 1
    state.hasMore = true
  }

  try {
    const data = await getActivities({
      page: state.page,
      pageSize: 10,
      category: state.category === "关注" ? undefined : state.category,
    })
    if (data && data.list) {
      state.list = reset ? data.list : [...state.list, ...data.list]
      state.hasMore = data.list.length >= 10
      state.page++
    }
  } catch {
    // 后端未就绪时使用 mock
    if (reset || state.list.length === 0) {
      state.list = activityFeed
      state.hasMore = false
    }
  } finally {
    state.loading = false
  }
}

/**
 * 切换分类
 * @param {string} category
 */
function setCategory(category) {
  state.category = category
  fetchList(true)
}

/**
 * 加载更多
 */
function loadMore() {
  if (!state.hasMore || state.loading) return
  fetchList()
}

/**
 * 根据 ID 查找已缓存的活动
 * @param {string} id
 * @returns {Object|undefined}
 */
function findById(id) {
  return state.list.find((item) => item.id === id)
}

export function useActivityStore() {
  return {
    state,
    fetchList,
    setCategory,
    loadMore,
    findById,
  }
}
