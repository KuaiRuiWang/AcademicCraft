/**
 * 环境变量配置说明
 *
 * 在项目根目录创建 .env.local 或 .env.development 文件，并添加以下配置：
 *
 * # API 基础地址
 * VITE_API_BASE_URL=http://localhost:8123/api
 *
 * 生产环境可以创建 .env.production 文件：
 *
 * # 生产环境配置示例
 * VITE_API_BASE_URL=https://api.your-domain.com
 *
 * 说明：部署地址不再配置域名，统一使用相对路径（跟随当前站点），
 * 避免写死域名导致部署链接指向错误主机。
 */

export {}
