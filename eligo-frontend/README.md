# Eligo 2.0 最小流程 Demo

基于 Vue 3 + Vite + UniApp，主要验收目标为微信小程序。

## 页面流程

1. Eligo 启动页
2. 品牌开屏页
3. 三页引导轮播
4. 活动广场
5. 活动详情
6. 我的
7. 个人资料编辑

启动页和品牌页会自动推进；首次打开、清除引导记录或主动退出后进入可左右滑动的引导页。点击“微信登录/注册”完成统一账号流程，点击“先逛逛”以游客身份进入活动广场。游客当前只可浏览活动广场和活动详情；其他页签和搜索、筛选、报名、收藏、关注、分享、发布等操作会进入登录页。登录成功后返回触发操作时所在页面，由用户再次确认操作，不自动提交报名或发布。

同一安装实例点击过“先逛逛”后，后续启动会直接进入活动广场；已有有效会话时也直接进入活动广场。

页面按 [`../docs/xml/figml-export.xml`](../docs/xml/figml-export.xml) 的 Frame 层级和坐标重建。UI 使用
`view`、`text`、`image`、`swiper`、`scroll-view` 和独立 Vue 组件实现，
没有整页设计截图或透明热区。

## 本地运行

```bash
npm install
npm run dev:mp-weixin
```

微信开发者工具导入目录：

```text
dist/dev/mp-weixin
```

生产构建：

```bash
npm run build:mp-weixin
```

构建后导入：

```text
dist/build/mp-weixin
```

联调和发布前，请确认 `src/manifest.json` 的 `mp-weixin.appid` 与后端微信配置属于同一小程序。开发者工具可以访问本机 API；真机必须通过 `VITE_API_BASE_URL` 注入微信已登记的 HTTPS 域名，不能使用 `127.0.0.1`。

个人资料页默认连接真实 API。如只检查页面视觉，可在构建前设置：

```bash
VITE_PROFILE_DATA_MODE=mock npm run build:mp-weixin
```

`mock` 模式会在页面显示标识并禁用资料、手机号和头像写入，不能作为联调结果。未设置或设置为 `api` 时，请求失败会显示真实错误，不会静默回退到 mock。

## 数据与素材

- 静态展示数据：`src/mock/events.js`、`dynamic.js`、`friend.js`
- 仅供显式视觉演示的资料数据：`src/mock/profile.js`
- 独立照片、插画和图标素材：`src/static/eligo`
- 设计颜色令牌：`src/uni.scss`
- 公共组件：`src/components`
- 页面配置：`src/pages.json`

设计稿使用的 OPPOSans、思源黑体、苹方和 D-DIN 字号/字重已按节点样式
还原。XML 不包含字体文件本体，因此微信小程序默认使用系统可用的同名字体
和苹方/微软雅黑回退字体；如需在所有设备上保持完全一致，需要再提供可分发的
字体文件。
