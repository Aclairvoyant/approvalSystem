import http from './http'
import type { components } from './api.d'

// 从生成的类型中导出常用类型
export type User = components['schemas']['User']
export type Application = components['schemas']['Application']
export type UserRelation = components['schemas']['UserRelation']
export type LoginRequest = components['schemas']['LoginRequest']
export type RegisterRequest = components['schemas']['RegisterRequest']
export type LoginResponse = components['schemas']['LoginResponse']
export type ApplicationCreateRequest = components['schemas']['ApplicationCreateRequest']
export type ApplicationApprovalRequest = components['schemas']['ApplicationApprovalRequest']
export type FileUploadResponse = components['schemas']['FileUploadResponse']
export type OperationLogDTO = components['schemas']['OperationLogDTO']
export type PageApplication = components['schemas']['PageApplication']
export type PageUserRelation = components['schemas']['PageUserRelation']

// API 响应类型
export type ApiResponse<T> = components['schemas']['ApiResponseVoid'] & { data?: T }

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
  updateApplication(id: number, data: ApplicationCreateRequest) {
    return http.put<Application>(`/applications/${id}`, data)
  },
  cancelApplication(id: number) {
    return http.post<void>(`/applications/${id}/cancel`)
  },
  sendVoiceNotification(id: number) {
    return http.post<void>(`/applications/${id}/send-voice-notification`)
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
