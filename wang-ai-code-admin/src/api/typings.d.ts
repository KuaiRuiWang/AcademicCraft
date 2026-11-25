declare namespace API {
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

  type UserVO = {
    id?: number
    userAccount?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    createTime?: string
  }
}
