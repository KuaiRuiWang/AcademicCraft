const templateBase = `${import.meta.env.BASE_URL}templates/`

export const starterTemplates = [
  {
    id: 'event-signup',
    name: '活动报名表',
    category: '活动组织',
    description: '登记参与人和人数，实时查看报名汇总。',
    prompt:
      '创建一个活动报名网站，展示活动时间、地点和简介。提供报名表单，记录称呼和报名人数，支持查看报名名单与人数汇总。使用示例数据。',
  },
  {
    id: 'inventory-ledger',
    name: '库存台账',
    category: '日常管理',
    description: '录入物品和数量，按物品汇总当前库存。',
    prompt:
      '创建一个小团队库存台账，支持录入物品、登记出入库、查询库存变动，并在首页展示当前数量和低库存提醒。使用示例数据。',
  },
  {
    id: 'team-board',
    name: '团队看板',
    category: '团队协作',
    description: '新增任务，在待办、进行中和已完成之间切换状态。',
    prompt:
      '创建一个团队任务看板，按待办、进行中、已完成组织任务，支持添加任务、设置负责人并更新任务状态。使用示例数据。',
  },
].map((template) => ({ ...template, url: `${templateBase}${template.id}.html` }))

export type StarterTemplate = (typeof starterTemplates)[number]
