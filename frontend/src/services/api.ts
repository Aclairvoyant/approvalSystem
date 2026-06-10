import http from './http'

// 从生成的类型中导出常用类型
export interface User {
  id: number
  username: string
  phone?: string
  email?: string
  password?: string
  realName?: string
  avatar?: string
  role: number
  voiceNotificationEnabled?: boolean
  status: number
  createdAt?: string
  updatedAt?: string
  admin?: boolean
}

export interface Application {
  id: number
  applicantId: number
  approverId: number
  title: string
  description: string
  appType?: number
  voiceTranscript?: string
  remark?: string
  status: number
  rejectReason?: string
  approvalDetail?: string
  applicantName?: string
  applicantUsername?: string
  approverName?: string
  approverUsername?: string
  createdAt: string
  approvedAt?: string
  updatedAt?: string
}

export interface UserRelation {
  id: number
  userId: number
  relatedUserId: number
  relationType?: number
  requesterId: number
  otherUserId: number
  otherUserName?: string
  otherUserUsername?: string
  otherUserPhone?: string
  otherUserEmail?: string
  otherUserAvatar?: string
  requesterName?: string
  requesterUsername?: string
  requesterAvatar?: string
  createdAt: string
  updatedAt?: string
}

export interface LoginRequest {
  username?: string
  password?: string
}

export interface RegisterRequest {
  username?: string
  phone?: string
  password?: string
  email?: string
  realName?: string
  emailVerificationCode?: string
}

export interface LoginResponse {
  token?: string
  userId?: number
  username?: string
  realName?: string
  phone?: string
  email?: string
  avatar?: string
  role?: number
  voiceNotificationEnabled?: boolean
}

export interface ApplicationCreateRequest {
  approverId: number
  title: string
  description: string
  remark?: string
  sendVoiceNotification?: boolean
}

export interface ApplicationApprovalRequest {
  approvalDetail?: string
}

// 语音解析结果：转写原文 + 抽取出的申请字段
export interface VoiceParseResult {
  transcript: string
  title?: string
  description?: string
  remark?: string
}

export interface FileUploadResponse {
  attachmentId?: number
  fileName?: string
  fileUrl?: string
  fileSize?: number
  fileType?: string
}

export interface OperationLogDTO {
  id: number
  applicationId: number
  operatorId: number
  operatorName?: string
  operationType: number
  operationTypeDesc?: string
  oldStatus?: number
  newStatus?: number
  operationDetail?: string
  createdAt: string
}

export interface PageApplication {
  records: Application[]
  total: number
  size: number
  current: number
  pages: number
}

export interface PageUserRelation {
  records: UserRelation[]
  total: number
  size: number
  current: number
  pages: number
}

// API 响应类型
export interface ApiResponse<T> {
  code?: number
  message?: string
  data?: T
}

// 分页参数
export interface PaginationParams {
  pageNum?: number
  pageSize?: number
}

// 用户更新请求
export interface UserUpdateRequest {
  realName?: string
  phone?: string
  email?: string
}

// 用户统计数据
export interface UserStats {
  myPending: number
  myApproved: number
  myRejected: number
  pendingForMe: number
  myRelations: number
}

// 仪表盘统计数据
export interface DashboardStats {
  totalApplications: number
  pendingApplications: number
  approvedApplications: number
  rejectedApplications: number
  totalUsers: number
  activeUsers: number
}

export interface MimoConfigResponse {
  enabled?: boolean
  baseUrl?: string
  asrModel?: string
  chatModel?: string
  apiKeyConfigured?: boolean
  apiKeyMasked?: string
}

// 认证相关 API
export const authAPI = {
  register(data: RegisterRequest) {
    return http.post<User>('/auth/register', data)
  },
  login(data: LoginRequest) {
    return http.post<LoginResponse>('/auth/login', data)
  },
  getUserInfo() {
    return http.get<User>('/auth/user-info')
  },
  updateProfile(data: UserUpdateRequest) {
    return http.put<User>('/auth/update-profile', data)
  },
  changePassword(oldPassword: string, newPassword: string) {
    return http.put<void>('/auth/change-password', { oldPassword, newPassword })
  },
  uploadAvatar(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return http.post<string>('/auth/upload-avatar', formData)
  },
  sendEmailCode(email: string) {
    return http.post<void>('/auth/send-email-code', { email })
  },
  changeEmail(password: string, newEmail: string, verificationCode: string) {
    return http.put<User>('/auth/change-email', { password, newEmail, verificationCode })
  },
}

// 申请相关 API
export const applicationAPI = {
  createApplication(data: ApplicationCreateRequest) {
    return http.post<Application>('/applications', data)
  },
  createVoiceApplication(approverId: number, audio: Blob) {
    const formData = new FormData()
    formData.append('approverId', String(approverId))
    formData.append('file', audio, 'voice.wav')
    return http.post<Application>('/applications/voice', formData)
  },
  updateApplication(id: number, data: ApplicationCreateRequest) {
    return http.put<Application>(`/applications/${id}`, data)
  },
  cancelApplication(id: number) {
    return http.post<void>(`/applications/${id}/cancel`)
  },
  sendVoiceNotification(id: number) {
    return http.post<void>(`/applications/${id}/send-voice-notification`)
  },
  // 语音解析：上传口述音频，返回转写文字与抽取出的申请字段（不创建申请）
  parseVoice(audio: Blob, language: 'auto' | 'zh' | 'en' = 'zh') {
    const formData = new FormData()
    formData.append('file', audio, 'voice.wav')
    formData.append('language', language)
    return http.post<VoiceParseResult>('/applications/voice-parse', formData)
  },
  transcribeVoiceApplication(id: number, language: 'auto' | 'zh' | 'en' = 'zh') {
    return http.post<string>(`/applications/${id}/transcribe-voice`, null, { params: { language } })
  },
  getMyApplications(params: PaginationParams & { status?: number }) {
    return http.get<PageApplication>('/applications/my-applications', { params })
  },
  getMyApprovals(params: PaginationParams & { status?: number }) {
    return http.get<PageApplication>('/applications/my-approvals', { params })
  },
  getPendingApplications(params: PaginationParams & { keyword?: string }) {
    return http.get<PageApplication>('/applications/pending', { params })
  },
  getApplicationDetail(id: number) {
    return http.get<Application>(`/applications/${id}`)
  },
  approveApplication(id: number, data: ApplicationApprovalRequest) {
    return http.post<void>(`/applications/${id}/approve`, data)
  },
  rejectApplication(id: number, data: ApplicationApprovalRequest) {
    return http.post<void>(`/applications/${id}/reject`, data)
  },
}

// 用户搜索结果类型
export interface SearchUserResult {
  id: number
  username: string
  realName?: string
  phone?: string
  email?: string
  avatar?: string
}

// 对象关系 API
export const relationAPI = {
  searchUser(keyword: string) {
    return http.get<SearchUserResult>('/relations/search-user', { params: { keyword } })
  },
  initiateRelation(targetUserId: number) {
    return http.post<void>(`/relations/request/${targetUserId}`)
  },
  acceptRelation(relatedUserId: number) {
    return http.post<void>(`/relations/accept/${relatedUserId}`)
  },
  rejectRelation(relatedUserId: number) {
    return http.post<void>(`/relations/reject/${relatedUserId}`)
  },
  getMyRelations(params: PaginationParams) {
    return http.get<PageUserRelation>('/relations/my-relations', { params })
  },
  getPendingRequests(params: PaginationParams) {
    return http.get<PageUserRelation>('/relations/pending-requests', { params })
  },
  deleteRelation(relatedUserId: number) {
    return http.delete<void>(`/relations/${relatedUserId}`)
  },
}

// 操作日志 API
export const logAPI = {
  getApplicationTimeline(applicationId: number, params?: PaginationParams) {
    return http.get<OperationLogDTO[]>(`/operation-logs/timeline/${applicationId}`, { params })
  },
}

// 附件 API
export const attachmentAPI = {
  uploadApplicationAttachment(applicationId: number, file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return http.post<FileUploadResponse>(`/attachments/application/${applicationId}`, formData)
  },
  uploadApprovalAttachment(applicationId: number, file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return http.post<FileUploadResponse>(`/attachments/approval/${applicationId}`, formData)
  },
  getApplicationAttachments(applicationId: number) {
    return http.get<FileUploadResponse[]>(`/attachments/application/${applicationId}`)
  },
  getApprovalAttachments(applicationId: number) {
    return http.get<FileUploadResponse[]>(`/attachments/approval/${applicationId}`)
  },
  deleteApplicationAttachment(attachmentId: number) {
    return http.delete<void>(`/attachments/application/${attachmentId}`)
  },
  deleteApprovalAttachment(attachmentId: number) {
    return http.delete<void>(`/attachments/approval/${attachmentId}`)
  },
}

// 管理员 API
export const adminAPI = {
  getDashboardStats() {
    return http.get<DashboardStats>('/admin/dashboard/stats')
  },
  getUserStats() {
    return http.get<UserStats>('/admin/user/stats')
  },
  getAllUsers(params: PaginationParams) {
    return http.get<{ records: User[]; total: number }>('/admin/users', { params })
  },
  getAllApplications(params: PaginationParams & { status?: number }) {
    return http.get<{ records: Application[]; total: number }>('/admin/applications', { params })
  },
  getAllNotifications(params: PaginationParams & { sendStatus?: number; notifyType?: number }) {
    return http.get<{ records: Notification[]; total: number }>('/admin/notifications', { params })
  },
  updateUserStatus(userId: number, status: number) {
    return http.put<User>(`/admin/users/${userId}/status`, null, { params: { status } })
  },
  updateUserRole(userId: number, role: number) {
    return http.put<User>(`/admin/users/${userId}/role`, null, { params: { role } })
  },
  updateVoiceNotificationPermission(userId: number, enabled: boolean) {
    return http.put<User>(`/admin/users/${userId}/voice-notification`, null, { params: { enabled } })
  },
  updateUserInfo(userId: number, data: { realName?: string; phone?: string; email?: string }) {
    return http.put<User>(`/admin/users/${userId}`, data)
  },
  adminApproveApplication(applicationId: number, approvalDetail?: string) {
    return http.post<void>(`/admin/applications/${applicationId}/approve`, null, {
      params: { approvalDetail }
    })
  },
  adminRejectApplication(applicationId: number, rejectReason?: string) {
    return http.post<void>(`/admin/applications/${applicationId}/reject`, null, {
      params: { rejectReason }
    })
  },
  getMimoConfig() {
    return http.get<MimoConfigResponse>('/admin/mimo/config')
  },
}

// 通知类型
export interface Notification {
  id: number
  applicationId: number
  notifyUserId: number
  notifyType: number
  notifyTitle: string
  notifyContent: string
  phone?: string
  email?: string
  sendStatus: number
  sendError?: string
  createdAt: string
  sentAt?: string
}

// 评论类型
export interface ApplicationComment {
  id: number
  applicationId: number
  userId: number
  userName: string
  userAvatar?: string
  content: string
  parentId?: number
  createdAt: string
  updatedAt?: string
  replies?: ApplicationComment[]
}

// 评论 API
export const commentAPI = {
  getApplicationComments(applicationId: number) {
    return http.get<ApplicationComment[]>(`/comments/application/${applicationId}`)
  },
  createComment(data: { applicationId: number; content: string; parentId?: number }) {
    return http.post<ApplicationComment>('/comments', data)
  },
  updateComment(commentId: number, content: string) {
    return http.put<ApplicationComment>(`/comments/${commentId}`, { content })
  },
  deleteComment(commentId: number) {
    return http.delete<void>(`/comments/${commentId}`)
  }
}

// ===================== 游戏相关类型和API =====================

// 任务位置信息
export interface TaskPositionInfo {
  position: number
  taskId: number | null
  title: string
}

// 游戏信息类型
export interface GameInfo {
  id: number
  gameCode: string
  player1Id: number
  player1Name: string | null
  player1Avatar: string | null
  player2Id: number | null
  player2Name: string | null
  player2Avatar: string | null
  currentTurn: number
  gameStatus: number
  winnerId: number | null
  player1Pieces: number[]
  player2Pieces: number[]
  lastDiceResult: number | null
  taskPositions: number[]  // 任务位置数组
  taskInfos: TaskPositionInfo[]  // 任务位置详细信息
  createdAt: string
  startedAt: string | null
  endedAt: string | null
}

// 游戏任务类型
export interface GameTask {
  id: number
  taskType: number
  creatorId: number | null
  category: string
  difficulty: number
  title: string
  description: string
  requirement: string
  timeLimit: number | null
  points: number
  usageCount: number
  createdAt: string
}

// 游戏任务记录类型
export interface GameTaskRecord {
  id: number
  gameId: number
  taskId: number
  triggerPlayerId: number
  executorPlayerId: number
  taskStatus: number
  completionNote: string | null
  triggeredPosition: number
  createdAt: string
  completedAt: string | null
}

// 游戏分页结果
export interface PageGameInfo {
  records: GameInfo[]
  total: number
  size: number
  current: number
  pages: number
}

export interface PageGameTask {
  records: GameTask[]
  total: number
  size: number
  current: number
  pages: number
}

// 游戏 API
export const gameApi = {
  // 创建游戏房间（支持自定义任务位置）
  createGame(opponentUserId: number, taskPositions?: number[]) {
    return http.post<GameInfo>('/game/create', { opponentUserId, taskPositions })
  },

  // 更新任务位置配置（房主在等待状态可用）
  updateTaskPositions(gameId: number, taskPositions: number[]) {
    return http.put<GameInfo>(`/game/${gameId}/task-positions`, taskPositions)
  },

  // 加入游戏房间
  joinGame(gameCode: string) {
    return http.post<GameInfo>('/game/join', { gameCode })
  },

  // 获取游戏详情
  getGameDetail(gameId: number) {
    return http.get<GameInfo>(`/game/${gameId}`)
  },

  // 获取用户游戏列表
  getUserGames(status?: number, pageNum = 1, pageSize = 10) {
    return http.get<PageGameInfo>('/game/list', {
      params: { status, pageNum, pageSize }
    })
  },

  // 取消游戏
  cancelGame(gameId: number) {
    return http.post<void>(`/game/${gameId}/cancel`)
  },

  // 强制结束游戏（房主可用）
  forceEndGame(gameId: number) {
    return http.post<void>(`/game/${gameId}/force-end`)
  },

  // 检查轮次
  checkTurn(gameId: number) {
    return http.get<boolean>(`/game/${gameId}/turn`)
  },

  // 心跳
  heartbeat(gameId: number) {
    return http.post<void>(`/game/${gameId}/heartbeat`)
  }
}

// 游戏任务 API
export const gameTaskApi = {
  // 创建自定义任务
  createCustomTask(data: {
    title: string
    description?: string
    requirement?: string
    category?: string
    difficulty?: number
    timeLimit?: number
    points?: number
  }) {
    return http.post<GameTask>('/game/task/create', data)
  },

  // 获取预设任务列表
  getPresetTasks(params?: {
    category?: string
    difficulty?: number
    pageNum?: number
    pageSize?: number
  }) {
    return http.get<PageGameTask>('/game/task/preset', { params })
  },

  // 获取用户自定义任务列表
  getUserCustomTasks(pageNum = 1, pageSize = 20) {
    return http.get<PageGameTask>('/game/task/custom', {
      params: { pageNum, pageSize }
    })
  },

  // 获取任务详情
  getTaskById(taskId: number) {
    return http.get<GameTask>(`/game/task/${taskId}`)
  },

  // 删除自定义任务
  deleteCustomTask(taskId: number) {
    return http.delete<void>(`/game/task/${taskId}`)
  },

  // 获取随机任务
  getRandomTask(category?: string) {
    return http.get<GameTask>('/game/task/random', { params: { category } })
  },

  // 完成任务
  completeTask(recordId: number, completionNote?: string) {
    return http.post<void>(`/game/task/record/${recordId}/complete`, { completionNote })
  },

  // 放弃任务
  abandonTask(recordId: number) {
    return http.post<void>(`/game/task/record/${recordId}/abandon`)
  },

  // 获取游戏任务记录
  getGameTaskRecords(gameId: number) {
    return http.get<GameTaskRecord[]>(`/game/task/record/game/${gameId}`)
  },

  // 获取当前任务
  getCurrentTask(gameId: number) {
    return http.get<GameTaskRecord | null>(`/game/task/record/game/${gameId}/current`)
  }
}

// Daily couple features
export interface DailyItem {
  id: number
  creatorId: number
  partnerId?: number
  itemType: number
  title: string
  content?: string
  status: number
  priority: number
  targetDate?: string
  completedAt?: string
  createdAt: string
  updatedAt?: string
}

export interface DailyItemRequest {
  partnerId?: number
  itemType?: number
  title: string
  content?: string
  priority?: number
  targetDate?: string
}

export interface CoupleEvent {
  id: number
  creatorId: number
  partnerId?: number
  eventType: number
  title: string
  eventDate: string
  repeatType: number
  remindDaysBefore: number
  note?: string
  createdAt: string
  updatedAt?: string
}

export interface CoupleEventRequest {
  partnerId?: number
  eventType?: number
  title: string
  eventDate: string
  repeatType?: number
  remindDaysBefore?: number
  note?: string
}

export interface ApplicationTemplate {
  id: number
  creatorId: number
  partnerId?: number
  title: string
  description: string
  remark?: string
  shared: boolean
  usageCount: number
  createdAt: string
  updatedAt?: string
}

export interface ApplicationTemplateRequest {
  partnerId?: number
  title: string
  description: string
  remark?: string
  shared?: boolean
}

export interface ReminderSummary {
  pendingApprovalCount: number
  todayDailyCount: number
  overdueDailyCount: number
  totalEventCount?: number
  upcomingEventCount: number
  pendingApprovals: Application[]
  todayDailyItems: DailyItem[]
  overdueDailyItems: DailyItem[]
  upcomingEvents: CoupleEvent[]
}

export const dailyAPI = {
  list(params?: { status?: number; itemType?: number; todayOnly?: boolean }) {
    return http.get<DailyItem[]>('/daily-items', { params })
  },
  create(data: DailyItemRequest) {
    return http.post<DailyItem>('/daily-items', data)
  },
  update(id: number, data: DailyItemRequest) {
    return http.put<DailyItem>(`/daily-items/${id}`, data)
  },
  complete(id: number) {
    return http.post<DailyItem>(`/daily-items/${id}/complete`)
  },
  archive(id: number) {
    return http.post<DailyItem>(`/daily-items/${id}/archive`)
  },
  delete(id: number) {
    return http.delete<void>(`/daily-items/${id}`)
  }
}

export const coupleEventAPI = {
  list() {
    return http.get<CoupleEvent[]>('/couple-events')
  },
  upcoming(days = 30) {
    return http.get<CoupleEvent[]>('/couple-events/upcoming', { params: { days } })
  },
  create(data: CoupleEventRequest) {
    return http.post<CoupleEvent>('/couple-events', data)
  },
  update(id: number, data: CoupleEventRequest) {
    return http.put<CoupleEvent>(`/couple-events/${id}`, data)
  },
  delete(id: number) {
    return http.delete<void>(`/couple-events/${id}`)
  }
}

export const applicationTemplateAPI = {
  list() {
    return http.get<ApplicationTemplate[]>('/application-templates')
  },
  create(data: ApplicationTemplateRequest) {
    return http.post<ApplicationTemplate>('/application-templates', data)
  },
  update(id: number, data: ApplicationTemplateRequest) {
    return http.put<ApplicationTemplate>(`/application-templates/${id}`, data)
  },
  use(id: number) {
    return http.post<ApplicationTemplate>(`/application-templates/${id}/use`)
  },
  delete(id: number) {
    return http.delete<void>(`/application-templates/${id}`)
  }
}

export const reminderAPI = {
  today() {
    return http.get<ReminderSummary>('/reminders/today')
  }
}
