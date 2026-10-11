# Jotang Note 前端

Vue 3 + Vite + TypeScript + Element Plus，前后端分离，接口契约见 Apifox（《概要设计》§9）。

## 开发

```bash
npm install
npm run dev      # http://localhost:5173
```

开发期 `/api` 由 Vite 代理到 `http://localhost:8080`（见 `vite.config.ts`），所以后端要起在本机 8080。前端代码里一律写相对路径 `/api/...`，不写死域名。

## 构建与部署

```bash
npm run build    # 产物在 dist/，交给 Nginx 托管
```

生产环境若 `/api` 与前端不同源，用 `VITE_API_BASE_URL` 指定后端地址；不设则用默认的 `/api`。

## 目录

```
src/
├── api/       # Axios 实例与拦截器、接口定义、后端契约类型（与 ErrorCode 一一对应）
├── router/    # 路由表与登录守卫
├── stores/    # Pinia，当前只有 auth
├── utils/     # token 本地存取
└── views/     # 页面
```

约定：

- 后端响应统一是 `{code, message, data}`，拦截器已剥壳，`api.get<T>()` 直接拿到 `data`；失败抛 `ApiError`（带 `code` 与 `data`）。
- 分支只按 `code`（`src/api/types.ts` 的 `ErrorCode`）判断，不解析 `message` 文案。
- 表单级错误码（`FORM_LEVEL_CODES`）由拦截器让给页面就地展示，不弹全局提示；其余错误码由拦截器统一 toast。
- `40100` 表示 token 失效，拦截器负责清登录态并跳登录页。
