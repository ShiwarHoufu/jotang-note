<script setup lang="ts">
import { ChatDotRound, Close, Loading, Plus } from '@element-plus/icons-vue'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { nextTick, ref, watch } from 'vue'

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
/** 本次页面生命周期内已经尝试过恢复——避免反复展开面板重复请求 */
const resumeAttempted = ref(false)
const resuming = ref(false)

async function scrollToBottom() {
  await nextTick()
  listEl.value?.scrollTo({ top: listEl.value.scrollHeight })
}

/**
 * 接回最近一次会话。挂在「首次展开面板」而不是 onMounted 上：组件挂在 MainLayout 里，
 * 每个页面都会加载它，而失败时 http 层会弹全局提示——挂 mount 上等于让从不使用 AI 的
 * 用户在翻页时也可能吃到一条无关报错。挂首次展开既贴合「打开对话界面时自动对接」，
 * 也把提示限定在用户主动打开面板的那一刻。
 */
async function resume() {
  if (resumeAttempted.value) {
    return
  }
  resumeAttempted.value = true
  resuming.value = true
  try {
    const res = await aiApi.latest()
    // 用户手快、在响应回来之前已经自己发出消息：放弃回填。
    // 否则旧会话的展示会盖掉刚发出去的气泡，conversationId 也会被覆盖回旧会话
    if (!res.conversationId || messages.value.length) {
      return
    }
    conversationId.value = res.conversationId
    messages.value = await Promise.all(
      res.messages.map(async (msg) => ({
        role: msg.role,
        content: msg.content,
        // 与 send() 走同一条 marked + DOMPurify 路径，不另起炉灶
        html:
          msg.role === 'ASSISTANT' && msg.content
            ? DOMPurify.sanitize(await marked.parse(msg.content))
            : '',
      })),
    )
  } catch {
    // 网络 / 服务异常：什么都不做，消息还在库里，刷新页面再试。
    // 这里不把 resumeAttempted 置回 false——否则每次展开面板都会失败重试并重弹一次全局提示
  } finally {
    resuming.value = false
    await scrollToBottom()
  }
}

watch(open, (opened) => {
  if (opened) {
    resume()
  }
})

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
    <Transition name="chat-panel">
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
          <TransitionGroup v-if="messages.length" name="chat-msg" tag="div" class="messages">
            <div
              v-for="(msg, i) in messages"
              :key="i"
              class="row"
              :class="msg.role === 'USER' ? 'mine' : 'theirs'"
            >
              <div v-if="msg.role === 'USER'" class="bubble user">{{ msg.content }}</div>
              <!-- eslint-disable-next-line vue/no-v-html -- 内容已过 DOMPurify（与 NotePreview 同一路径） -->
              <div v-else-if="msg.content" class="bubble assistant markdown-body" v-html="msg.html" />
              <div v-else class="bubble assistant pending">
                <el-icon class="is-loading"><Loading /></el-icon>
              </div>
            </div>
          </TransitionGroup>
          <!-- 恢复期间先占位，避免闪一下「还没有消息」的空状态再被历史顶上 -->
          <div v-else-if="resuming" class="row theirs">
            <div class="bubble assistant pending">
              <el-icon class="is-loading"><Loading /></el-icon>
            </div>
          </div>
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
    </Transition>

    <button
      class="fab"
      type="button"
      aria-label="AI 助手"
      :aria-expanded="open"
      @click="open = !open"
    >
      <!-- 打开时图标切到关闭，和面板的开合动效同步 -->
      <Transition name="fab-icon" mode="out-in">
        <el-icon v-if="open" key="close" :size="24"><Close /></el-icon>
        <el-icon v-else key="chat" :size="26"><ChatDotRound /></el-icon>
      </Transition>
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
  transition: transform 0.15s ease, box-shadow 0.2s ease;
}

.fab:hover {
  transform: scale(1.06);
}

.fab:active {
  transform: scale(0.94);
}

/* 打开时加一圈品牌色光晕，和面板的状态呼应 */
.fab[aria-expanded='true'] {
  box-shadow:
    0 0 0 6px rgba(79, 124, 255, 0.12),
    var(--el-box-shadow);
}

.panel {
  width: 380px;
  max-width: calc(100vw - 48px);
  height: min(560px, calc(100vh - 120px));
  background: #fff;
  border-radius: var(--jt-radius);
  box-shadow: var(--el-box-shadow-dark);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 面板从悬浮球方向浮出 */
.chat-panel-enter-active,
.chat-panel-leave-active {
  transition: opacity 0.22s ease, transform 0.22s cubic-bezier(0.22, 0.61, 0.36, 1);
  transform-origin: bottom right;
}

.chat-panel-enter-from,
.chat-panel-leave-to {
  opacity: 0;
  transform: translateY(12px) scale(0.96);
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

.messages {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 新消息从底部淡入；失败撤回的占位气泡向上淡出 */
.chat-msg-enter-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.chat-msg-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.chat-msg-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.chat-msg-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.chat-msg-move {
  transition: transform 0.2s ease;
}

/* 悬浮球图标：聊天 ↔ 关闭的旋转淡入淡出 */
.fab-icon-enter-active,
.fab-icon-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.fab-icon-enter-from {
  opacity: 0;
  transform: scale(0.6) rotate(-45deg);
}

.fab-icon-leave-to {
  opacity: 0;
  transform: scale(0.6) rotate(45deg);
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

@media (prefers-reduced-motion: reduce) {
  .fab,
  .chat-panel-enter-active,
  .chat-panel-leave-active,
  .chat-msg-enter-active,
  .chat-msg-leave-active,
  .chat-msg-move,
  .fab-icon-enter-active,
  .fab-icon-leave-active {
    transition: none;
  }
}
</style>
