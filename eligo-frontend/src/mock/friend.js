/**
 * 社交关系模块 mock 数据
 * 后端 M4 模块未实现前使用
 */

export const mockNotifications = [
  {
    id: "1",
    type: "activity_change",
    title: "活动时间变更",
    content: "你报名的「深圳园博园第18届港人北上活动」时间调整为 7/14 15:30",
    time: "1小时前",
    read: false,
  },
  {
    id: "2",
    type: "enroll_success",
    title: "报名成功",
    content: "你已成功报名「天文台 & 鹿嘴山庄徒步」活动",
    time: "3小时前",
    read: false,
  },
  {
    id: "3",
    type: "system",
    title: "欢迎加入 Eligo",
    content: "完善你的个人资料，发现更多精彩活动吧！",
    time: "1天前",
    read: true,
  },
]

export const mockFollowings = [
  { id: "1", name: "深圳湾港人科技园", avatar: "/static/eligo/photos/avatar-woman.png", isOfficial: true },
  { id: "2", name: "暴苑—Eligo", avatar: "/static/eligo/photos/avatar-woman-2.png", isOfficial: false },
  { id: "3", name: "上桂青", avatar: "/static/eligo/photos/avatar-man-glasses.png", isOfficial: false },
]

export const mockFollowers = [
  { id: "4", name: "吳希磊", avatar: "/static/eligo/photos/avatar-man.png" },
  { id: "5", name: "尉朋順", avatar: "/static/eligo/photos/avatar-western.png" },
]
