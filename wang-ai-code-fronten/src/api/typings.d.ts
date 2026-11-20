declare namespace API {
  type AppAddRequest = {
    initPrompt?: string
    generateBackend?: boolean
    generateThesis?: boolean
    thesisTitle?: string
  }

  type AppAdminUpdateRequest = {
    id?: number
    appName?: string
    cover?: string
    priority?: number
  }

  type AppDeployRequest = {
    appId?: number
  }

  type AppQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    appName?: string
    cover?: string
    initPrompt?: string
    codeGenType?: string
    deployKey?: string
    priority?: number
    userId?: number
  }

  type AppUpdateRequest = {
    id?: number
    appName?: string
  }

  type AppVO = {
    generateThesis?: number
    thesisTitle?: string
    id?: number
    appName?: string
    cover?: string
    initPrompt?: string
    codeGenType?: string
    deployKey?: string
    deployedTime?: string
    priority?: number
    userId?: number
    createTime?: string
    updateTime?: string
    user?: UserVO
  }

  type BaseResponseAppVO = {
    code?: number
    data?: AppVO
    message?: string
  }

  type BaseResponseBoolean = {
    code?: number
    data?: boolean
    message?: string
  }

  type BaseResponseListProjectCategory = {
    code?: number
    data?: ProjectCategory[]
    message?: string
  }

  type BaseResponseListProjectReviewVO = {
    code?: number
    data?: ProjectReviewVO[]
    message?: string
  }

  type BaseResponseLoginUserVO = {
    code?: number
    data?: LoginUserVO
    message?: string
  }

  type BaseResponseLong = {
    code?: number
    data?: number
    message?: string
  }

  type BaseResponsePageAppVO = {
    code?: number
    data?: PageAppVO
    message?: string
  }

  type BaseResponsePageChatHistory = {
    code?: number
    data?: PageChatHistory
    message?: string
  }

  type BaseResponsePageProjectOrderVO = {
    code?: number
    data?: PageProjectOrderVO
    message?: string
  }

  type BaseResponsePageProjectVO = {
    code?: number
    data?: PageProjectVO
    message?: string
  }

  type BaseResponsePageUserVO = {
    code?: number
    data?: PageUserVO
    message?: string
  }

  type BaseResponseProjectOrderVO = {
    code?: number
    data?: ProjectOrderVO
    message?: string
  }

  type BaseResponseProjectVO = {
    code?: number
    data?: ProjectVO
    message?: string
  }

  type BaseResponseString = {
    code?: number
    data?: string
    message?: string
  }

  type BaseResponseUser = {
    code?: number
    data?: User
    message?: string
  }

  type BaseResponseUserVO = {
    code?: number
    data?: UserVO
    message?: string
  }

  type ChatHistory = {
    renderData?: string
    id?: number
    message?: string
    messageType?: string
    appId?: number
    userId?: number
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type ChatHistoryQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    message?: string
    messageType?: string
    appId?: number
    userId?: number
    lastCreateTime?: string
  }

  type chatToGenCodeParams = {
    appId: number
    message: string
  }

  type DeleteRequest = {
    id?: number
  }

  type DiagramTask = {
    mermaidCode?: string
    description?: string
  }

  type downloadAppCodeParams = {
    appId: number
  }

  type downloadProjectParams = {
    orderId: number
  }

  type executeWorkflowParams = {
    prompt: string
  }

  type executeWorkflowWithFluxParams = {
    prompt: string
  }

  type getAppVOByIdByAdminParams = {
    id: number
  }

  type getAppVOByIdParams = {
    id: number
  }

  type getOrderVOByIdParams = {
    id: number
  }

  type getProjectVOByIdByAdminParams = {
    id: number
  }

  type getProjectVOByIdParams = {
    id: number
  }

  type getUserByIdParams = {
    id: number
  }

  type getUserVOByIdParams = {
    id: number
  }

  type IllustrationTask = {
    query?: string
  }

  type ImageCollectionPlan = {
    contentImageTasks?: ImageSearchTask[]
    illustrationTasks?: IllustrationTask[]
    diagramTasks?: DiagramTask[]
    logoTasks?: LogoTask[]
  }

  type ImageResource = {
    category?: 'CONTENT' | 'LOGO' | 'ILLUSTRATION' | 'ARCHITECTURE'
    description?: string
    url?: string
  }

  type ImageSearchTask = {
    query?: string
  }

  type listAppChatHistoryParams = {
    appId: number
    pageSize?: number
    lastCreateTime?: string
  }

  type listReviewsParams = {
    projectId: number
  }

  type LoginUserVO = {
    id?: number
    userAccount?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    createTime?: string
    updateTime?: string
  }

  type LogoTask = {
    description?: string
  }

  type PageAppVO = {
    records?: AppVO[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type PageChatHistory = {
    records?: ChatHistory[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type PageProjectOrderVO = {
    records?: ProjectOrderVO[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type PageProjectVO = {
    records?: ProjectVO[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type PageUserVO = {
    records?: UserVO[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type ProjectAddRequest = {
    name?: string
    categoryId?: number
    techTags?: string
    description?: string
    detailContent?: string
    coverImage?: string
    price?: number
    sourceType?: number
    sourceUrl?: string
    sourceAttribution?: string
    licenseTerms?: string
    demoUrl?: string
    images?: ProjectImageItem[]
  }

  type ProjectCategory = {
    id?: number
    name?: string
    icon?: string
    sortOrder?: number
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type ProjectImageItem = {
    imageUrl?: string
    sortOrder?: number
    type?: number
  }

  type ProjectImageVO = {
    imageUrl?: string
    sortOrder?: number
    type?: number
  }

  type ProjectOrderCreateRequest = {
    projectId?: number
  }

  type listMyOrdersParams = {
    pageNum?: number
    pageSize?: number
    projectId?: number
  }

  type ProjectOrderVO = {
    id?: number
    projectId?: number
    projectName?: string
    projectCover?: string
    buyerId?: number
    sellerId?: number
    amount?: number
    status?: number
    sourceType?: number
    downloadAvailable?: boolean
    payTime?: string
    createTime?: string
  }

  type ProjectQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    name?: string
    categoryId?: number
    techTag?: string
    status?: number
    authorId?: number
    minPrice?: number
    maxPrice?: number
  }

  type ProjectReviewAddRequest = {
    projectId?: number
    orderId?: number
    rating?: number
    content?: string
  }

  type ProjectReviewVO = {
    id?: number
    projectId?: number
    userId?: number
    rating?: number
    content?: string
    createTime?: string
    user?: UserVO
  }

  type ProjectUpdateRequest = {
    id?: number
    name?: string
    categoryId?: number
    techTags?: string
    description?: string
    detailContent?: string
    coverImage?: string
    price?: number
    sourceType?: number
    sourceUrl?: string
    sourceAttribution?: string
    licenseTerms?: string
    demoUrl?: string
    status?: number
    images?: ProjectImageItem[]
  }

  type ProjectVO = {
    id?: number
    name?: string
    categoryId?: number
    categoryName?: string
    techTags?: string
    description?: string
    detailContent?: string
    coverImage?: string
    price?: number
    sourceType?: number
    sourceUrl?: string
    sourceAttribution?: string
    licenseTerms?: string
    demoUrl?: string
    authorId?: number
    status?: number
    salesCount?: number
    viewCount?: number
    avgRating?: number
    createTime?: string
    updateTime?: string
    author?: UserVO
    images?: ProjectImageVO[]
  }

  type QualityResult = {
    isValid?: boolean
    errors?: string[]
    suggestions?: string[]
  }

  type ServerSentEventString = true

  type serveStaticResourceParams = {
    deployKey: string
  }

  type User = {
    id?: number
    userAccount?: string
    userPassword?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    vipExpireTime?: string
    vipCode?: string
    vipNumber?: number
    editTime?: string
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type UserAddRequest = {
    userName?: string
    userAccount?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
  }

  type UserLoginRequest = {
    userAccount?: string
    userPassword?: string
  }

  type UserProfileUpdateRequest = {
    userName?: string
    userAvatar?: string
    userProfile?: string
  }

  type UserQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    userName?: string
    userAccount?: string
    userProfile?: string
    userRole?: string
  }

  type UserRegisterRequest = {
    userAccount?: string
    userPassword?: string
    checkPassword?: string
  }

  type UserUpdateRequest = {
    id?: number
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
  }

  type UserVO = {
    id?: number
    userAccount?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    createTime?: string
  }

  type WorkflowContext = {
    appId?: number
    currentStep?: string
    originalPrompt?: string
    imageListStr?: string
    imageList?: ImageResource[]
    enhancedPrompt?: string
    generationType?: 'HTML' | 'MULTI_FILE' | 'VUE_PROJECT'
    generatedCodeDir?: string
    buildResultDir?: string
    errorMessage?: string
    qualityResult?: QualityResult
    imageCollectionPlan?: ImageCollectionPlan
    contentImages?: ImageResource[]
    illustrations?: ImageResource[]
    diagrams?: ImageResource[]
    logos?: ImageResource[]
  }
}
