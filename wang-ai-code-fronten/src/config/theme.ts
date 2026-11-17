import type { ThemeConfig } from 'ant-design-vue/es/config-provider/context'

/**
 * 全站主题：明亮高端风（Soft Structuralism）
 * 暖白背景 + 单一深青强调色 + 暖灰边框 + 统一圆角
 *
 * 注：ant-design-vue 4.2.6 的组件级 token 类型定义不完整（运行时 antd v5 均支持），
 * 因此 components 部分做类型放宽，token 部分保持严格校验。
 */
export const themeConfig: ThemeConfig = {
  token: {
    colorPrimary: '#0F766E',
    colorInfo: '#0F766E',
    colorSuccess: '#0F766E',
    colorBgLayout: '#F7F6F3',
    colorBgContainer: '#FFFFFF',
    colorBgElevated: '#FFFFFF',
    colorText: '#1C1917',
    colorTextSecondary: '#57534E',
    colorTextTertiary: '#746D67',
    colorTextPlaceholder: '#746D67',
    colorError: '#B93838',
    colorBorder: '#E7E5E4',
    colorBorderSecondary: '#F0EFED',
    colorSplit: '#E7E5E4',
    colorBgTextHover: '#F5F5F4',
    colorBgTextActive: '#EEEDE9',
    borderRadius: 8,
    borderRadiusLG: 12,
    borderRadiusSM: 8,
    fontFamily:
      "'Plus Jakarta Sans Variable', 'PingFang SC', 'Microsoft YaHei', -apple-system, BlinkMacSystemFont, sans-serif",
    controlOutline: 'rgba(15, 118, 110, 0.12)',
    controlHeight: 38,
    controlHeightLG: 46,
  },
  components: {
    Layout: {
      siderBg: '#16181D',
      triggerBg: '#101216',
      headerBg: '#FFFFFFF2',
      headerColor: '#1C1917',
    },
    Menu: {
      darkItemBg: '#16181D',
      darkSubMenuItemBg: '#101216',
      darkItemSelectedBg: '#1F2933',
      darkItemColor: '#A8A29E',
      darkItemHoverColor: '#FFFFFF',
      darkItemSelectedColor: '#FFFFFF',
      itemBorderRadius: 8,
    },
    Table: {
      headerBg: '#FAF9F7',
      headerColor: '#57534E',
      rowHoverBg: '#FAF9F7',
      borderColor: '#F0EFED',
      headerSplitColor: '#F0EFED',
    },
    Button: {
      primaryShadow: 'none',
      fontWeight: 600,
    },
    Card: {
      colorBorderSecondary: '#F0EFED',
    },
    Modal: {
      borderRadiusLG: 16,
    },
  },
} as ThemeConfig
