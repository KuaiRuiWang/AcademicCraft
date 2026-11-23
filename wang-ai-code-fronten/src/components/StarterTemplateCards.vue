<script setup lang="ts">
import { starterTemplates, type StarterTemplate } from '@/config/starterTemplates'
import ProjectCard from '@/components/ProjectCard.vue'

withDefaults(defineProps<{ templates?: StarterTemplate[] }>(), {
  templates: () => starterTemplates,
})
</script>

<template>
  <ProjectCard
    v-for="template in templates"
    :key="template.id"
    :to="'/project/' + template.id"
    :title="template.name"
    :category="template.category"
    :description="template.description"
    :illustration="template.id"
  />
</template>
