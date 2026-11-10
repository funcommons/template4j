import { defineConfig } from 'vitepress'

// https://vitepress.dev/reference/site-config
export default defineConfig({
  lang: 'zh-CN',
  title: 'template4j',
  description: 'JSON / YAML 声明式模板引擎 — 9 组件 BFF / API 编排, Groovy 脚本融合, 零依赖核心',
  themeConfig: {
    // https://vitepress.dev/reference/default-theme-config
    nav: [
      { text: '首页', link: '/' },
      { text: '快速开始', link: '/guide/' },
      { text: '组件总览', link: '/components' },
      { text: 'GitHub', link: 'https://github.com/funcommons/template4j' }
    ],
    sidebar: {
      '/guide/': [
        {
          text: '指南',
          items: [{ text: '快速开始', link: '/guide/' }]
        }
      ],
      '/components': [
        {
          text: '参考',
          items: [{ text: '组件总览', link: '/components' }]
        }
      ]
    },
    socialLinks: [
      { icon: 'github', link: 'https://github.com/funcommons/template4j' }
    ],
    outline: 'deep',
    footer: {
      message: 'template4j — JSON / YAML 声明式模板引擎',
      copyright: 'Copyright © 2026 funcommons'
    }
  }
})
