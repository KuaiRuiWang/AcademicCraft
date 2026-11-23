---
name: "百夜应用工坊"
description: "纸上工坊视觉系统"
colors:
  primary: "#2443ae"
  primary-strong: "#1b3288"
  primary-soft: "#e8edfb"
  primary-hover: "#4562ba"
  on-primary: "#ffffff"
  bg: "#f8f5ed"
  surface: "#fdfbf6"
  surface-soft: "#ece8dc"
  text: "#202019"
  text-secondary: "#625f53"
  text-tertiary: "#6b6658"
  border: "#cdc7b9"
  success: "#376249"
  success-bg: "#e6efe7"
  success-border: "#b7c9ba"
  warning: "#935e18"
  error: "#a0302e"
typography:
  display:
    fontFamily: "'Noto Serif SC Variable', 'Songti SC', SimSun, Georgia, serif"
    fontSize: "clamp(3rem, 5.9vw, 5.75rem)"
    fontWeight: 750
    lineHeight: 1.19
    letterSpacing: "-0.06em"
  headline:
    fontFamily: "'Noto Serif SC Variable', 'Songti SC', SimSun, Georgia, serif"
    fontSize: "clamp(2rem, 4vw, 3.6rem)"
    fontWeight: 750
    lineHeight: 1.24
    letterSpacing: "-0.04em"
  title:
    fontFamily: "'Noto Serif SC Variable', 'Songti SC', SimSun, Georgia, serif"
    fontSize: "clamp(1.45rem, 2.5vw, 2.25rem)"
    fontWeight: 750
    lineHeight: 1.24
    letterSpacing: "-0.04em"
  body:
    fontFamily: "'Plus Jakarta Sans Variable', 'Microsoft YaHei UI', 'PingFang SC', sans-serif"
    fontSize: "0.9375rem"
    fontWeight: 400
    lineHeight: 1.65
  label:
    fontFamily: "'Plus Jakarta Sans Variable', 'Microsoft YaHei UI', 'PingFang SC', sans-serif"
    fontWeight: 650
rounded:
  md: "2px"
spacing:
  page-gutter: "max(18px, 4vw)"
  page-space: "clamp(28px, 4.2vw, 64px)"
  panel-padding: "clamp(18px, 2.3vw, 32px)"
  action-gap: "10px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    rounded: "{rounded.md}"
    height: "43px"
    padding: "9.5px 15px"
  button-primary-hero:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    rounded: "{rounded.md}"
    height: "46px"
    padding: "0 21px"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.md}"
    height: "43px"
    padding: "9.5px 15px"
  input:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.md}"
    height: "43px"
    padding: "9.5px 11px"
  category-filter:
    backgroundColor: "transparent"
    textColor: "{colors.text}"
    rounded: "{rounded.md}"
    padding: "5px 11px"
  category-filter-selected:
    backgroundColor: "{colors.text}"
    textColor: "{colors.surface}"
    rounded: "{rounded.md}"
    padding: "5px 11px"
  nav-tab-current:
    textColor: "{colors.primary}"
    height: "44px"
  paper-panel:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.md}"
    padding: "{spacing.panel-padding}"
  brief:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.md}"
    padding: "16px 18px"
---

# Design System — 百夜应用工坊

## Overview

**纸上工坊**

暖米白底、墨色文字和中文衬线标题建立纸面层次。首页使用原型的蓝色折纸照片，模板目录用小幅封面、标题和细分隔线呈现条目。

创作页让需求简报和交付清单并列。对话、论文编辑和管理沿用同一套字色、分隔线和表单控件，突出当前任务和操作状态。

## Colors

### Primary

- **钴蓝**用于主要操作、链接、导航当前位置和全局键盘焦点。首页主按钮悬停用深钴蓝，Ant Design Vue 常规主按钮使用组件主题生成的蓝色悬停态。
- **浅钴蓝**用于智能体工具结果的状态底色。

### Neutral

- **暖米白**是整页底色，**亮纸白**用于面板、输入框和弹层，**浅灰米色**用于代码区、工具信息和章节悬停。
- **墨色**用于正文、标题和明确的结构线。灰褐色区分说明文字、占位提示和细分隔线。
- 组件主题用绿色、琥珀色和暗红色表达成功、提醒和错误，配合可读的状态文字。

## Typography

首页标语、页面标题和分区标题使用 Noto Serif SC Variable，回退到系统宋体及衬线字体。正文、导航与表单使用 Plus Jakarta Sans Variable，中文回退到微软雅黑或苹方。页面简介限制在 64ch，列表、表格和分页的数字使用等宽数字字形。

## Layout

- 页面内容居中，最大宽度 1256px。首页标题与主图并排，模板选集三栏。视口至 760px 时选集改为单栏，至 520px 时主图移到首屏文案下方。
- 创作简报与交付清单、账号页图文、应用设置相关工作区使用同一纸面宽度。至 760px 时创作和账号布局转为单栏，模板发布设置与正文也按顺序堆叠。
- 对话工作区填满可用视高，消息独立滚动，输入框停靠底部。宽度达到 960px 时默认双栏，可拖动分隔线调宽，或切换到仅对话和仅成果。窄屏通过标签查看对话、应用预览与论文初稿。论文目录随实际栏宽切换为横向章节条。
- 模板详情正文与侧栏至 800px 时堆叠，详情首屏图文和模板目录条目至 600px 时各自调整。个人项目及领取记录至 620px 时把操作排到文案下方。
- 管理页用栏目导航、换行筛选和表格。记录区可横向滚动，窄屏收紧单元格间距并保留行内操作。

## Elevation & Depth

纸色层次和边线组织内容。常规按钮与纸面面板保持平面。导航当前位置用内嵌蓝线，菜单覆盖层用半透明墨色遮罩指示层级。

## Shapes

按钮、输入和面板使用近直角的纸张边缘。首页和页面标题用墨色横线分隔，目录和资料行用细灰褐线，交付清单以 3px 钴蓝顶线标明分区。

## Components

- **按钮与链接**。主要操作用钴蓝实底，次要操作用纸白描边，页内跳转常用钴蓝文字链接。常规操作沿用 Ant Design Vue 组件，首页主行动按钮使用更高的尺寸。
- **导航和筛选**。栏目用钴蓝文字与下边线标明当前位置。窄屏主导航收进「导航」菜单。目录分类用边框按钮，选中态为墨色实底。
- **字段和反馈**。字段标签沿用表单控件的可读层级，简报文本框使用纸白底，提供样例与字数提示。加载、空结果、错误和可重试状态使用页面骨架、空态或带操作的提示。
- **目录与交付**。目录条目并列小幅封面、分类、标题、描述和入口。交付清单用成对的项目名与值呈现输出，并随创作选项变化。
- **管理与编辑工作区**。管理表格、对话面板、论文章节和保存操作沿用共享控件。用标题、分隔线和文本状态保留扫描顺序。

## Do's and Don'ts

- 将原型折纸照片用于首页主图。创作页桌面交付清单及账号页沿用同一图像。
- 保持当前栏目、筛选和状态都有可读的文字，并保留键盘焦点反馈。
- 用 `src/assets/global.css` 和 `src/config/theme.ts` 对照共享令牌与组件主题。
