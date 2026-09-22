/**
 * 动态模块 mock 数据
 * 后端 M4 模块未实现前使用
 */

export const mockDynamics = [
  {
    id: "1",
    user: {
      name: "深圳湾港人科技园",
      avatar: "/static/eligo/photos/avatar-woman.png",
      isOfficial: true,
    },
    content: "今天组织了一场精彩的港人北上活动，大家玩得很开心！期待下次再聚！",
    images: ["/static/eligo/photos/event-1.png", "/static/eligo/photos/event-2.png"],
    likes: 128,
    comments: 24,
    time: "2小时前",
    isLiked: false,
  },
  {
    id: "2",
    user: {
      name: "Marc Wilkins",
      avatar: "/static/eligo/photos/avatar-western.png",
      isOfficial: false,
    },
    content: "周末和朋友们一起去公园摄影，拍了很多漂亮的照片！",
    images: ["/static/eligo/photos/event-3.png"],
    likes: 56,
    comments: 8,
    time: "5小时前",
    isLiked: false,
  },
  {
    id: "3",
    user: {
      name: "Eligo 官方",
      avatar: "/static/eligo/photos/avatar-man.png",
      isOfficial: true,
    },
    content: "感谢大家对我们平台的支持！我们会继续努力，为大家提供更好的服务！",
    images: [],
    likes: 256,
    comments: 42,
    time: "1天前",
    isLiked: true,
  },
]
