<script setup lang="ts">
import { ref, watch } from 'vue'
import { FileTextOutlined } from '@ant-design/icons-vue'

const props = defineProps<{ illustration?: string; cover?: string }>()
const imageFailed = ref(false)
watch(
  () => props.cover,
  () => {
    imageFailed.value = false
  },
)
</script>

<template>
  <div class="template-art" :class="illustration" aria-hidden="true">
    <img
      v-if="cover && !imageFailed"
      :src="cover"
      alt=""
      loading="lazy"
      @error="imageFailed = true"
    />
    <template v-else-if="illustration === 'event-signup'">
      <span class="ticket ticket-back"></span>
      <span class="ticket"><i></i><i></i><i></i></span>
    </template>
    <span v-else-if="illustration === 'inventory-ledger'" class="shelf">
      <i v-for="box in 6" :key="box"></i>
    </span>
    <span v-else-if="illustration === 'team-board'" class="kanban">
      <span v-for="column in 3" :key="column"><i></i><i></i></span>
    </span>
    <FileTextOutlined v-else class="generic-art" />
  </div>
</template>

<style scoped>
.template-art {
  --art-ink: #32499a;
  --art-bg: #dce4f2;
  --art-face: #f6f9ff;
  position: relative;
  min-width: 0;
  display: grid;
  place-items: center;
  overflow: hidden;
  height: 85px;
  color: var(--art-ink);
  background: var(--art-bg);
  container-type: size;
}

.template-art img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.inventory-ledger {
  --art-ink: #4f603c;
  --art-bg: #e2e6d6;
  --art-face: #f4f6ec;
}

.team-board {
  --art-ink: #98512d;
  --art-bg: #f3dfc9;
  --art-face: #fff1df;
}

.ticket {
  width: min(62%, 160px);
  height: 48%;
  max-height: 88px;
  position: relative;
  border: 2px solid var(--art-ink);
  border-radius: 6px;
  background: var(--art-face);
  transform: rotate(-10deg);
}

.ticket-back {
  position: absolute;
  background: var(--art-ink);
  transform: translate(10%, -8%) rotate(8deg);
}

.ticket:not(.ticket-back)::after {
  content: '';
  position: absolute;
  inset-block: 0;
  inset-inline-end: 23%;
  border-inline-start: 2px dashed var(--art-ink);
}

.ticket i {
  position: absolute;
  inset-inline-start: 10%;
  inset-block-start: 24%;
  width: 38%;
  height: 20%;
  background: var(--art-ink);
}

.ticket i:nth-child(2) {
  inset-block-start: 54%;
  width: 49%;
  height: 8%;
}

.ticket i:nth-child(3) {
  inset-block-start: 70%;
  width: 33%;
  height: 8%;
}

.shelf {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  width: min(58%, 156px);
  gap: clamp(2px, 4cqw, 5px);
  transform: skewY(-6deg);
  border-bottom: 3px solid var(--art-ink);
}

.shelf i {
  position: relative;
  aspect-ratio: 1.9;
  background: var(--art-face);
  border: 2px solid var(--art-ink);
}

.shelf i::after {
  content: '';
  position: absolute;
  inset: 0 auto auto 40%;
  width: 18%;
  height: 32%;
  background: var(--art-ink);
}

.shelf i:first-child {
  visibility: hidden;
}

.shelf i:nth-child(3n) {
  background: var(--art-ink);
}

.kanban {
  display: grid;
  width: min(73%, 170px);
  height: 53%;
  grid-template-columns: repeat(3, 1fr);
  gap: clamp(2px, 3cqw, 5px);
  transform: rotate(-5deg);
}

.kanban span {
  padding: 12%;
  border: 2px solid var(--art-ink);
  border-radius: 4px;
  background: var(--art-face);
}

.kanban i {
  display: block;
  height: 25%;
  margin-bottom: 15%;
  border-radius: 2px;
  background: var(--art-ink);
}

.kanban span + span i:nth-child(2),
.kanban span:nth-child(2) i {
  opacity: 0.45;
}

.generic-art {
  font-size: 2rem;
}
</style>
