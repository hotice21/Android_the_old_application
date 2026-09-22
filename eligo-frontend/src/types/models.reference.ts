/**
 * 基础用户实体接口
 */
export interface User {
  id: string;
  avatar: string;        // 用户头像 URL
  nickname: string;      // 用户昵称
  age: number;
  gender: 'male' | 'female';
  location: string;      // 用户常驻地理位置
  interestTags: string[]; // 兴趣标签数组
  isVerified: boolean;   // 是否通过官方认证
  followers: number;     // 粉丝数
  following: number;     // 关注数
  likes: number;         // 获赞总数
}

/**
 * 活动（Activity）实体接口
 */
export interface Activity {
  id: string;
  title: string;
  type: 'official' | 'partner'; // 活动类型：官方自营 / 合作伙伴
  image: string;                 // 活动主视觉封面图 URL
  /**
   * 活动组织者/发布方精简信息（去噪后的非对称嵌套）
   */
  organizer: {
    id: string;
    name: string;
    avatar: string;
    isOfficial: boolean;
  };
  price: number | null;     // 活动费用（null 代表免费或待定）
  rating: number;           // 活动评分（例如 4.8）
  reviewCount: number;      // 评价总数
  /**
   * 活动举办时间
   * 强制约束：必须严格遵循 ISO 8601 标准格式（如 2026-07-12T14:14:48Z），
   * 确保跨时区解析安全，避免 8 小时时区断层。
   */
  date: string;
  location: string;         // 活动举办具体地点
  /**
   * 活动地点经纬度坐标
   * 替代原静态 distance 字段，交由前端设备结合 GPS SDK 动态计算实时距离，
   * 避免后端返回"脏数据"导致离线/移动状态下距离失真。
   */
  latitude: number;         // 纬度
  longitude: number;        // 经度
  tags: string[];           // 活动分类标签（如 ['户外', '极限运动']）
  description: string;      // 活动详情富文本/纯文本描述
  isFavorite: boolean;      // 当前用户是否已收藏该活动
}

/**
 * 社区动态（朋友圈/广场帖子）实体接口
 */
export interface Dynamic {
  id: string;
  userId: string;
  user: User;               // 动态发布者的完整用户信息映射
  images: string[];         // 动态配图 URL 数组（支持多图）
  content: string;          // 动态文字正文
  activityType?: string;    // 关联的活动类型（可选属性）
  likes: number;            // 点赞数
  comments: number;         // 评论数
  isLiked: boolean;         // 当前用户是否已点赞
  isFavorite: boolean;      // 当前用户是否已收藏该动态
  /**
   * 动态发布时间
   * 强制约束：必须严格遵循 ISO 8601 标准格式（如 2026-07-12T14:14:48Z）。
   */
  createdAt: string;
}

/**
 * 即时通讯（IM）单条消息实体接口
 */
export interface Message {
  id: string;
  senderId: string;         // 发送方用户 ID
  receiverId: string;       // 接收方用户 ID
  type: 'text' | 'image' | 'voice'; // 消息多媒体类型
  content: string;          // 消息内容（文本字串、图片 URL 或音频 URL）
  /**
   * 发送时间
   * 强制约束：必须严格遵循 ISO 8601 标准格式（如 2026-07-12T14:14:48Z）。
   */
  timestamp: string;
  isRead: boolean;          // 接收方是否已读
}

/**
 * 评论实体接口
 * 用于动态详情页的评论列表渲染
 */
export interface Comment {
  id: string;               // 评论唯一标识
  dynamicId: string;        // 所属动态 ID
  user: User;               // 评论者完整用户信息
  content: string;          // 评论正文
  /**
   * 评论发布时间
   * 强制约束：必须严格遵循 ISO 8601 标准格式（如 2026-07-12T14:14:48Z）。
   */
  createdAt: string;
  likes: number;            // 评论点赞数
  isLiked: boolean;         // 当前用户是否已点赞该评论
}

/**
 * IM 会话实体接口
 * 用于朋友模块会话列表的高频刷新渲染
 */
export interface Conversation {
  id: string;               // 会话唯一标识
  user: User;               // 对方用户完整信息
  /**
   * 最后一条消息预览
   */
  lastMessage: {
    content: string;        // 消息预览文本
    type: 'text' | 'image' | 'voice'; // 消息类型
    /**
     * 发送时间
     * 强制约束：必须严格遵循 ISO 8601 标准格式（如 2026-07-12T14:14:48Z）。
     */
    timestamp: string;
  };
  unreadCount: number;      // 未读消息数
  isMuted: boolean;         // 是否免打扰
}

/**
 * 全局用户鉴权与登录状态机上下文接口
 */
export interface AuthState {
  isLoggedIn: boolean;      // 核心凭证：当前终端是否处于登录激活状态
  token: string | null;     // 鉴权 JWT Bearer Token
  user: User | null;        // 当前登录用户的个人核心 profile 缓存
}

/**
 * 统一后端 HTTP 响应网络外壳网关（泛型抽象）
 * @template T 后端真正返回并包裹在 data 字段中的业务实体核心数据结构
 */
export interface ApiResponse<T = any> {
  code: number;             // 业务约定的状态码（如 200 代表绝对成功）
  message: string;          // 提示/错误明文信息
  data: T;                  // 泛型业务载荷
}
