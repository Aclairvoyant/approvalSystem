# 审批管理系统

一个完整的双向审批管理系统，支持移动端和后台管理。用户可以互相创建申请，对方可以在移动端或电脑端进行审批，支持图片上传，并能实时查看处理流程。

## 项目特点

✅ **双向审批**：只有互为对象的用户才能相互创建申请和审批
✅ **移动端优化**：专为手机设计的界面，支持离线操作
✅ **电脑管理后台**：使用 Arco Design 组件库
✅ **图片上传**：支持审批时上传图片，存储到阿里云 OSS
✅ **完整流程记录**：详细的时间线展示所有操作
✅ **多渠道通知**：支持短信和邮件通知
✅ **TypeScript**：前端使用完整的 TypeScript 类型支持

## 技术栈

### 后端
- **Framework**: Spring Boot 3.5.9
- **ORM**: MyBatis Plus 3.5.5
- **Database**: MySQL 8.0+
- **Authentication**: JWT + Spring Security
- **File Storage**: 阿里云 OSS
- **Notifications**: 阿里云短信 + 邮件（SMTP）

### 前端
- **Framework**: Vue 3 + TypeScript
- **Build**: Vite 5
- **Component**: Arco Design + Vant
- **State Management**: Pinia
- **Routing**: Vue Router 4
- **HTTP Client**: Axios

## 项目结构

```
approval-system/
├── database.sql              # 数据库初始化脚本
├── backend/
│   ├── pom.xml
│   ├── src/main/
│   │   ├── java/com/approval/system/
│   │   │   ├── ApprovalSystemApplication.java
│   │   │   ├── entity/           # 实体类
│   │   │   ├── dto/              # 数据传输对象
│   │   │   ├── mapper/           # MyBatis Mapper
│   │   │   ├── service/          # 业务逻辑层
│   │   │   ├── controller/       # 控制层
│   │   │   └── common/
│   │   │       ├── config/       # 配置文件
│   │   │       ├── utils/        # 工具类
│   │   │       ├── enums/        # 枚举
│   │   │       ├── exception/    # 异常处理
│   │   │       ├── response/     # 响应类
│   │   │       ├── security/     # 安全相关
│   │   │       └── schedule/     # 定时任务
│   │   └── resources/
│   │       └── application.yml   # 配置文件
│   └── pom.xml
├── frontend/
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── index.html
│   └── src/
│       ├── main.ts
│       ├── App.vue
│       ├── pages/
│       │   ├── auth/             # 认证页面
│       │   ├── mobile/           # 移动端页面
│       │   └── admin/            # 管理端页面
│       ├── layouts/              # 布局组件
│       ├── router/               # 路由配置
│       ├── store/                # 状态管理
│       ├── services/             # API 服务
│       └── env.d.ts
```

## 数据库表设计

### 核心表
- **users**: 用户信息表
- **user_relations**: 用户关系表（互为对象关系）
- **applications**: 申请单表
- **operation_logs**: 操作日志表（流程记录）
- **application_attachments**: 申请附件表
- **approval_attachments**: 审批附件表
- **notifications**: 通知记录表

## 安装使用

### 后端配置

1. **创建数据库**
```bash
mysql -u root -p < database.sql
```

2. **配置 application.yml**
```yaml
# 数据库配置
spring.datasource.url: jdbc:mysql://localhost:3306/approval_system
spring.datasource.username: root
spring.datasource.password: root

# JWT 配置
jwt.secret: your-secret-key-256-bits-or-longer
jwt.expiration: 86400000  # 24小时

# 阿里云短信配置
aliyun.sms.access-key-id: your-access-key-id
aliyun.sms.access-key-secret: your-access-key-secret
aliyun.sms.sign-name: your-sign-name
aliyun.sms.template-code: your-template-code

# 阿里云 OSS 配置
aliyun.oss.endpoint: https://oss-cn-hangzhou.aliyuncs.com
aliyun.oss.access-key-id: your-access-key-id
aliyun.oss.access-key-secret: your-access-key-secret
aliyun.oss.bucket-name: your-bucket-name
aliyun.oss.bucket-url: https://your-bucket-name.oss-cn-hangzhou.aliyuncs.com
aliyun.oss.max-file-size: 10485760  # 10MB
```

3. **启动后端**
```bash
cd backend
mvn clean install
mvn spring-boot:run
```

### 前端配置

1. **安装依赖**
```bash
cd frontend
npm install
```

2. **开发模式**
```bash
npm run dev
```

3. **生产构建**
```bash
npm run build
npm run preview
```

## API 接口

### 认证相关
- `POST /api/auth/register` - 用户注册
- `POST /api/auth/login` - 用户登录
- `GET /api/auth/user-info` - 获取用户信息

### 申请管理
- `POST /api/applications` - 创建申请
- `GET /api/applications/my-applications` - 获取我的申请列表
- `GET /api/applications/pending` - 获取待审批列表
- `GET /api/applications/{id}` - 获取申请详情
- `POST /api/applications/{id}/approve` - 审批通过
- `POST /api/applications/{id}/reject` - 审批驳回

### 对象关系
- `POST /api/relations/request/{targetUserId}` - 发起对象申请
- `POST /api/relations/accept/{relatedUserId}` - 接受对象申请
- `POST /api/relations/reject/{relatedUserId}` - 拒绝对象申请
- `GET /api/relations/my-relations` - 获取我的对象列表
- `DELETE /api/relations/{relatedUserId}` - 删除对象关系

### 操作日志
- `GET /api/operation-logs/timeline/{applicationId}` - 获取申请的处理流程

### 附件管理
- `POST /api/attachments/application/{applicationId}` - 上传申请附件
- `POST /api/attachments/approval/{applicationId}` - 上传审批附件
- `GET /api/attachments/application/{applicationId}` - 获取申请附件列表
- `GET /api/attachments/approval/{applicationId}` - 获取审批附件列表
- `DELETE /api/attachments/application/{attachmentId}` - 删除申请附件
- `DELETE /api/attachments/approval/{attachmentId}` - 删除审批附件

## 功能流程

### 基本使用流程

1. **注册/登录**
   - 用户注册账号后登录系统

2. **添加对象**
   - 搜索并添加另一个用户为对象
   - 对方接受或拒绝申请
   - 建立互为对象关系

3. **创建申请**
   - 在对象列表中选择审批人
   - 填写事项标题、描述、备注
   - 可选上传图片附件
   - 提交申请

4. **审批流程**
   - 审批人收到申请通知（短信/邮件）
   - 在移动端查看申请详情
   - 查看完整的处理时间线
   - 填写审批意见，可选上传图片
   - 选择批准或驳回

5. **查看结果**
   - 申请人可查看审批结果
   - 实时看到完整的处理流程
   - 所有操作记录都保存在时间线中

## 系统特性

### 移动端优势
- ✅ 响应式设计，适配各种屏幕
- ✅ 底部导航栏快速切换功能
- ✅ 直观的申请卡片展示
- ✅ 一键审批操作
- ✅ 清晰的时间线流程展示

### 后台管理
- ✅ 统计仪表盘
- ✅ 申请数据管理
- ✅ 用户管理
- ✅ 通知记录查询
- ✅ Arco Design 专业外观

### 安全特性
- ✅ JWT 身份验证
- ✅ Spring Security 权限控制
- ✅ 对象关系验证（只有互为对象才能申请）
- ✅ 申请人和审批人权限隔离
- ✅ 密码加密存储

### 通知功能
- ✅ 申请创建时通知
- ✅ 审批完成时通知
- ✅ 支持短信通知
- ✅ 支持邮件通知
- ✅ 异步处理，不阻塞主流程

## 开发指南

### 添加新的 API 端点

1. 在 `entity` 包创建实体类
2. 在 `mapper` 包创建 Mapper 接口
3. 在 `service` 包创建 Service 接口和实现
4. 在 `controller` 包创建 Controller
5. 在前端 `services/api.ts` 添加 API 调用

### 前端页面开发

1. 创建 `.vue` 文件（使用 `<script setup lang="ts">`）
2. 使用 TypeScript 定义类型
3. 使用 Pinia store 管理状态
4. 使用 Arco Design 或 Vant 组件
5. 在 router 中配置路由

## 常见问题

**Q: 如何配置阿里云 OSS？**
A: 需要在阿里云创建 OSS bucket，获取 AccessKey，然后在 application.yml 中配置。

**Q: 短信和邮件如何配置？**
A: 短信使用阿里云短信服务，需要创建签名和模板。邮件使用 SMTP，可使用 QQ 邮箱或其他支持 SMTP 的邮箱。

**Q: 怎样修改前端的样式？**
A: 编辑各个 `.vue` 文件的 `<style scoped>` 部分，或修改 `variables.scss` 全局样式。

**Q: 如何添加新的通知渠道？**
A: 在 `INotificationService` 中添加新的通知方法，在 `NotificationServiceImpl` 实现，然后在需要通知的地方调用。

## 许可证

MIT

## 作者

Claude Code
