<script setup lang="ts">
import { ChatDotRound, Close, Loading, Plus } from '@element-plus/icons-vue'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { nextTick, ref } from 'vue'

import { aiApi } from '@/api/ai'

interface ChatMessage {
  role: 'USER' | 'ASSISTANT'
  content: string
  /** ASSISTANT 消息的预渲染 HTML（marked + DOMPurify，与 NotePreview 同一条消毒路径） */
  html?: string
}

const open = ref(false)
const sending = ref(false)
const input = ref('')
const conversationId = ref<string>()
const messages = ref<ChatMessage[]>([])
const listEl = ref<HTMLElement>()

async function scrollToBottom() {
  await nextTick()
  listEl.value?.scrollTo({ top: listEl.value.scrollHeight })
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) {
    return
  }
  input.value = ''
  sending.value = true
  messages.value.push({ role: 'USER', content: text })
  // 先占一个气泡：成功填内容，失败整体移除（42900/50300 等已由 http 层全局提示）
  const pending: ChatMessage = { role: 'ASSISTANT', content: '' }
  messages.value.push(pending)
  await scrollToBottom()
  try {
    const res = await aiApi.chat(text, conversationId.value)
    conversationId.value = res.conversationId
    pending.content = res.reply
    // 模型可能返回空串，空回复不是错误（《功能说明》§1.3）
    pending.html = res.reply ? DOMPurify.sanitize(await marked.parse(res.reply)) : ''
  } catch {
    messages.value.pop()
  } finally {
    sending.value = false
    await scrollToBottom()
  }
}

/** 开启新对话：清空本地消息并丢弃 conversationId，下一条即新会话 */
function reset() {
  messages.value = []
  conversationId.value = undefined
}
</script>

<template>
  <div class="ai-widget">
    <div v-if="open" class="panel">
      <div class="panel-header">
        <span class="panel-title">AI 助手</span>
        <div class="panel-actions">
          <el-tooltip content="开启新对话" placement="top">
            <el-button link :icon="Plus" :disabled="sending" @click="reset" />
          </el-tooltip>
          <el-button link :icon="Close" @click="open = false" />
        </div>
      </div>

      <div ref="listEl" class="panel-body">
        <template v-if="messages.length">
          <div
            v-for="(msg, i) in messages"
            :key="i"
            class="row"
            :class="msg.role === 'USER' ? 'mine' : 'theirs'"
          >
            <div v-if="msg.role === 'USER'" class="bubble user">{{ msg.content }}</div>
            <!-- eslint-disable-next-line vue/no-v-html -- 内容已过 DOMPurify（与 NotePreview 同一路径） -->
            <div v-else-if="msg.content" class="bubble assistant markdown" v-html="msg.html" />
            <div v-else class="bubble assistant pending">
              <el-icon class="is-loading"><Loading /></el-icon>
            </div>
          </div>
        </template>
        <el-empty
          v-else
          :image-size="80"
          description="问我任何笔记相关问题，例如「高等数学期末笔记讲了什么」"
        />
      </div>

      <div class="panel-footer">
        <el-input
          v-model="input"
          type="textarea"
          :rows="3"
          resize="none"
          placeholder="输入问题，Enter 发送，Shift+Enter 换行"
          :disabled="sending"
          @keydown.enter.exact.prevent="send"
        />
        <el-button type="primary" :loading="sending" :disabled="!input.trim()" @click="send">
          发送
        </el-button>
      </div>
    </div>

    <button class="fab" type="button" aria-label="AI 助手" @click="open = !open">
      <el-icon :size="26"><ChatDotRound /></el-icon>
    </button>
  </div>
</template>

<style scoped>
.ai-widget {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 99;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
}

.fab {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  border: none;
  cursor: pointer;
  color: #fff;
  background: var(--el-color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--el-box-shadow);
  transition: transform 0.15s ease;
}

.fab:hover {
  transform: scale(1.06);
}

.panel {
  width: 380px;
  max-width: calc(100vw - 48px);
  height: min(560px, calc(100vh - 120px));
  background: #fff;
  border-radius: 12px;
  box-shadow: var(--el-box-shadow-dark);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.panel-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: var(--el-fill-color-light);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.row {
  display: flex;
}

.row.mine {
  justify-content: flex-end;
}

.row.theirs {
  justify-content: flex-start;
}

.bubble {
  max-width: 85%;
  padding: 10px 12px;
  border-radius: 10px;
  line-height: 1.6;
  font-size: 14px;
  overflow-wrap: break-word;
  white-space: pre-wrap;
}

.bubble.user {
  background: var(--el-color-primary);
  color: #fff;
  border-top-right-radius: 2px;
}

.bubble.assistant {
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-top-left-radius: 2px;
  white-space: normal;
}

.bubble.pending {
  display: flex;
  align-items: center;
  min-height: 40px;
  color: var(--el-text-color-secondary);
}

.panel-footer {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.panel-footer .el-textarea {
  flex: 1;
}

/* AI 回复的 markdown 渲染（对应 :class="markdown" 的 assistant 气泡） */
.markdown :deep(p) {
  margin: 0 0 8px;
}

.markdown :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown :deep(pre) {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
  margin: 8px 0;
}

.markdown :deep(code) {
  font-family: Consolas, Monaco, monospace;
  font-size: 13px;
}

.markdown :deep(img) {
  max-width: 100%;
}

.markdown :deep(table) {
  border-collapse: collapse;
}

.markdown :deep(th),
.markdown :deep(td) {
  border: 1px solid #dcdfe6;
  padding: 6px 10px;
}
</style>
