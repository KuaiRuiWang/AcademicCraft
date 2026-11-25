<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
const props = defineProps<{ content: string }>()
const markdown = new MarkdownIt({ html: false, linkify: true })
const rendered = computed(() => markdown.render(props.content))
</script>

<template>
  <article class="markdown-content" v-html="rendered"></article>
</template>
