<template>
  <div class="main-layout">
    <div class="chat-list-panel" :class="{ 'collapsed': !isSidebarOpen }">
      <div class="chat-list-header">
        <h3>聊天记录</h3>
        <button @click="createNewSession" class="new-chat-button">
          + 新建聊天
        </button>
      </div>
      <div class="chat-sessions-list">
        <div
            v-for="session in chatSessions"
            :key="session.id"
            :class="['chat-item', { 'active': session.id === currentSessionId }]"
            @click="switchChat(session.id)"
        >
          <div class="chat-item-icon">💬</div>
          <div class="chat-item-content">
            <div class="chat-item-title">{{ session.title }}</div>
            <div class="chat-item-preview">
              {{ session.messages.length > 0 ? session.messages[0].text.substring(0, 20) + '...' : '新聊天' }}
            </div>
          </div>
          <button @click.stop="deleteSession(session.id)" class="delete-session-button">
            🗑️
          </button>
        </div>
      </div>
      <div class="sidebar-toggle" @click="toggleSidebar">
        <span v-if="isSidebarOpen">&lt;</span>
        <span v-else>&gt;</span>
      </div>
    </div>
    <<div class="left-panel" :class="{ 'shifted-left': isSidebarOpen }">
    <div v-if="isEditingMode" class="quill-toolbar-container">
      <div id="quill-fixed-toolbar"></div>
    </div>
    <div class="document-container" ref="documentContainer">
      <div v-for="msg in messages" :key="msg.id">

        <div v-if="msg.sender === 'ai'" class="document-content">
          <div v-if="!msg.isEditing">
            <div v-html="msg.text"
                 class="ai-formatted-content"
                 @dblclick="enableEdit(msg)"
                 @mouseup="handleAIMessageSelection($event, msg)">
            </div>

            <div class="message-actions ai-actions">
              <button @click="enableEdit(msg)" class="edit-button" title="编辑">
                ✎
              </button>
              <button @click="deleteMessage(msg.id)" class="delete-button" title="删除">
                🗑️
              </button>
            </div>
          </div>

          <div v-else class="ai-editor-container">
            <QuillEditor
                :key="currentEditingId"
                v-model:content="msg.tempText"
                contentType="html"
                theme="snow"
                :options="editorOptions"
                class="ai-quill-editor"
                @ready="onQuillReady(msg, $event)" />
            <div class="editor-actions">
              <button @click="saveEdit(msg)" class="save-button">保存</button>
              <button @click="cancelEdit(msg)" class="cancel-button">取消</button>
            </div>
          </div>
        </div>
      </div>

      <div v-if="messages.length === 0" class="empty-state">
        <div class="empty-icon">💬</div>
        <div class="empty-text">开始与AI对话吧</div>
        <div class="empty-hint">输入消息后按Enter或点击发送按钮</div>
      </div>
    </div>
    </div>
    <div class="right-panel" :class="{ 'shifted-right': isSidebarOpen }">
      <div class="prompt-suggestions-panel">
        <h4>常用旅游提示生成</h4>
        <div class="suggestion-list-container">
          <div v-for="(category, key) in promptCategories" :key="key" class="suggestion-category">
            <h4>{{ category.label }}</h4>
            <div class="suggestion-options">
              <div
                  v-for="option in category.options"
                  :key="option.id"
                  :class="['suggestion-item', { 'selected': selectedOptions[key] === option.id }]"
                  @click="selectOption(key, option.id)"
              >
                {{ option.text }}
              </div>
            </div>
          </div>
        </div>
        <div class="action-buttons">
          <button @click="generateFromSuggestions" :disabled="!isGenerateEnabled" class="generate-button">生成</button>
          <button @click="clearSelections" class="clear-button">清除选择</button>
        </div>
      </div>

      <div class="user-message-panel" ref="userMessagePanel" @mouseup="handleUserMessageSelection">
        <div v-for="(msg) in filteredMessages('user')" :key="msg.id">
          <div :class="['message-bubble', msg.sender]">
            <div class="message-header">
              <div class="message-avatar">{{ msg.sender === 'user' ? '👤' : '🤖' }}</div>
              <div class="message-info">
                <div class="message-sender">{{ msg.sender === 'user' ? '你' : 'AI助手' }}</div>
                <div class="message-time">{{ msg.time }}</div>
              </div>
            </div>

            <div v-if="editingMessageId === msg.id" class="message-content-edit">
          <textarea
              v-model="editingMessageText"
              @keyup.enter.exact="confirmEdit"
              class="edit-input"
              rows="3"
              ref="editInput"
          ></textarea>
              <div class="edit-buttons">
                <button @click="confirmEdit" class="edit-button-confirm"><span>确认</span><span>✓</span></button>
                <button @click="cancelEdit" class="edit-button-cancel"><span>取消</span><span>✕</span></button>
              </div>
            </div>

            <div v-else class="message-content">
              <div class="message-text">{{ msg.text }}</div>
              <div v-if="msg.sender === 'user'" class="message-actions">
                <button @click="startEdit(msg)" class="edit-button">
                  <svg viewBox="0 0 24 24" width="16" height="16">
                    <path fill="currentColor" d="M20.71,7.04C21.1,6.65 21.1,6 20.71,5.63L18.37,3.29C18,2.9 17.35,2.9 16.96,3.29L15.12,5.12L18.87,8.87M3,17.25V21H6.75L17.81,9.93L14.06,6.18L3,17.25Z"/>
                  </svg>
                </button>
                <button @click="deleteMessage(msg.id)" class="delete-button">
                  <svg viewBox="0 0 24 24" width="16" height="16">
                    <path fill="currentColor" d="M19,4H15.5L14.5,3H9.5L8.5,4H5V6H19M6,19A2,2 0 0,0 8,21H16A2,2 0 0,0 18,19V7H6V19Z"/>
                  </svg>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="input-container">
        <div class="input-area">
      <textarea
          v-model="inputMessage"
          @keydown.enter.exact.prevent="sendMessage"
          placeholder="输入消息..."
          class="message-input"
          rows="1"
          ref="messageInput"
      ></textarea>
          <button @click="clearChat" class="clear-button">
            🗑️ 清空
          </button>
          <button @click="sendMessage" class="send-button" :disabled="isSending">
            <span v-if="!isSending" class="button-text">发送</span>
            <span v-else class="button-text">发送中...</span>
            <span class="button-icon">
          <svg v-if="!isSending" viewBox="0 0 24 24" width="20" height="20">
            <path fill="currentColor" d="M2,21L23,12L2,3V10L17,12L2,14V21Z" />
          </svg>
          <span v-else class="spinner"></span>
        </span>
          </button>
        </div>
        <div class="input-footer">
          <div class="character-count">{{ inputMessage.length }}/1000</div>
          <div class="input-hint">Shift+Enter换行</div>
        </div>
      </div>
    </div>

    <!-- 悬浮面板 -->
    <div
        v-if="showFloatingPanel"
        class="floating-panel"
        :style="panelStyle"
        ref="floatingPanel"
    >
      <div class="panel-header">
        <h4>AI 助手</h4>
        <button class="close-button" @click="showFloatingPanel = false">&times;</button>
      </div>
      <div class="panel-body">
        <div class="preset-buttons">
          <button
              v-for="preset in regenerationPresets"
              :key="preset.id"
              class="preset-button"
              @click="applyPreset(preset.prompt)"
          >
            {{ preset.label }}
          </button>
        </div>
        <div class="divider"></div>
        <div class="custom-prompt">
          <textarea
              v-model="customPrompt"
              placeholder="输入你的新要求..."
              rows="2"
              ref="customPromptTextarea"
          ></textarea>
          <button @click="regenerateWithCustomPrompt" :disabled="!customPrompt.trim()">确认</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import axios from 'axios';
import { v4 as uuidv4 } from 'uuid';
import { QuillEditor } from '@vueup/vue-quill';
import Quill from 'quill';
// import '@vueup/vue-quill/dist/vue-quill.bubble.css'; // ✅ 使用 bubble 主题样式
import '@vueup/vue-quill/dist/vue-quill.snow.css';

const fonts = ['宋体', '微软雅黑', '黑体', '楷体', '仿宋', '等线', 'sans-serif', 'serif', 'monospace'];

export default {
  name: 'Chat',
  components: { QuillEditor },
  data() {
    return {
      currentEditingId: null,
      isEditingMode: false,
      messages: [],
      inputMessage: '',
      apiBaseUrl: '/api/chat',
      editingMessageId: null,
      editingMessageText: '',
      isSending: false,
      darkMode: false,

      chatSessions: [],
      currentSessionId: null,
      isSidebarOpen: true,

      // 这里存放“原生 Quill 实例”，键为消息 id
      quillRefs: {},

      promptCategories: {
        theme: {
          label: '主题',
          options: [
            { id: 't1', text: '旅游情况周报' },
            { id: 't2', text: '交通数据分析' },
            { id: 't3', text: '美食建议' },
            { id: 't4', text: '动漫发展' },
            { id: 't5', text: '旅游攻略推荐' },
          ]
        },
        tone: {
          label: '语气',
          options: [
            { id: 'o1', text: '正式' },
            { id: 'o2', text: '口语化' },
            { id: 'o3', text: '简明' },
            { id: 'o4', text: '详细' },
          ]
        },
        length: {
          label: '字数',
          options: [
            { id: 'l1', text: '200字' },
            { id: 'l2', text: '800字' },
            { id: 'l3', text: '1500字' },
          ]
        },
        format: {
          label: '文章格式',
          options: [
            { id: 'report', text: '报告' },
            { id: 'essay', text: '论文' },
            { id: 'example', text: '举例' },
          ]
        }
      },
      selectedOptions: { theme: null, tone: null, length: null, format: null },

      showFloatingPanel: false,
      selectedText: '',
      selectedRange: null,
      activeMessage: null,
      panelStyle: { top: '0', left: '0' },
      customPrompt: '',

      activeQuillInstance: null,   // 原生 Quill 实例
      currentSelection: null,      // { index, length }

      regenerationPresets: [
        { id: '1', label: 'AI润色', prompt: '润色这段文字' },
        { id: '2', label: '重新生成', prompt: '重新生成这段文字' },
        { id: '3', label: '更正式', prompt: '用更正式的语言' },
        { id: '4', label: '更精炼', prompt: '用更精炼的语言' },
      ],


      editorOptions: {
        modules: {
          toolbar: [
            [{ header: [1, 2, 3, 4, 5, 6, false] }],
            [{ size: ['small', false, 'large', 'huge'] }],
            ['bold', 'italic', 'underline', 'strike'],
            [{ list: 'ordered' }, { list: 'bullet' }],
            [{ color: [] }, { background: [] }],
            [{ font: fonts }],
            [{ align: [] }],
            ['clean']
          ],
          clipboard: {
            // ✅ 关键修改：禁用对粘贴内容的智能处理，直接保留 HTML
            matchVisual: false
          }
        },
        placeholder: '编辑内容...',
        readOnly: false,

      },
    };
  },

  created() {
    this.darkMode = localStorage.getItem('darkMode') === 'true';
    const savedSessions = localStorage.getItem('chatSessions');
    if (savedSessions) {
      try {
        this.chatSessions = JSON.parse(savedSessions);
        if (this.chatSessions.length > 0) {
          this.currentSessionId = this.chatSessions[0].id;
          this.loadCurrentSession();
        } else {
          this.initDefaultSession();
        }
      } catch (e) {
        console.error('Failed to parse chat sessions from localStorage:', e);
        localStorage.removeItem('chatSessions');
        this.initDefaultSession();
      }
    } else {
      this.initDefaultSession();
    }
  },

  mounted() {
    const Font = Quill.import('formats/font');
    const fonts = ['宋体', '微软雅黑', '黑体', '楷体', '仿宋', '等线', 'sans-serif', 'serif', 'monospace'];

    Font.whitelist = fonts;
    Quill.register(Font, true);
    this.adjustTextareaHeight(this.$refs.messageInput);
    this.$nextTick(() => {
      document.addEventListener('mousedown', this.handleDocumentClick);
    });
  },

  beforeUnmount() {
    document.removeEventListener('mousedown', this.handleDocumentClick);
    Object.keys(this.quillRefs).forEach(key => {
      if (this.quillRefs[key]) {
        delete this.quillRefs[key];
      }
    });
    },

  watch: {
    inputMessage(newVal) {
      this.adjustTextareaHeight(this.$refs.messageInput);
      if (newVal.length > 1000) {
        this.inputMessage = newVal.slice(0, 1000);
      }
    },
    darkMode(newVal) {
      localStorage.setItem('darkMode', newVal);
      document.documentElement.setAttribute('data-theme', newVal ? 'dark' : 'light');
    }
  },

  methods: {

    // handleQuillReady(id, quillInstance) {
    //   this.quillRefs[id] = quillInstance;
    //
    //   // 设置内容
    //   // const msg = this.messages.find(m => m.id === id);
    //   // if (msg && msg.isEditing) {
    //   //   // 直接设置HTML内容
    //   //   quillInstance.clipboard.dangerouslyPasteHTML(0, msg.tempText);
    //   //   quillInstance.focus();
    //   // }
    // },

    cleanHtmlForQuill(html) {
      // 移除可能引起问题的标签和属性
      const div = document.createElement('div');
      div.innerHTML = html;

      // 移除所有样式属性
      const elementsWithStyle = div.querySelectorAll('[style]');
      elementsWithStyle.forEach(el => el.removeAttribute('style'));

      // 移除可能引起问题的标签
      const problematicTags = div.querySelectorAll('script, style, link, meta, title');
      problematicTags.forEach(el => el.remove());

      return div.innerHTML;
    },

    toggleSidebar() {
      this.isSidebarOpen = !this.isSidebarOpen;
    },
    enableEdit(msg) {
      if (this.currentEditingId === msg.id) return;

      if (this.currentEditingId) {
        const prevMsg = this.messages.find(m => m.id === this.currentEditingId);
        if (prevMsg) {
          prevMsg.isEditing = false;
          // 清理之前的 Quill 实例
          if (this.quillRefs[prevMsg.id]) {
            delete this.quillRefs[prevMsg.id];
          }
        }
      }

      this.currentEditingId = msg.id;
      msg.isEditing = true;
      this.isEditingMode = true;

      // 只保存原始文本，不设置 tempText
      msg.originalText = msg.text;
      // 清空 tempText，让 onQuillReady 来设置
      msg.tempText = '';

      this.$nextTick(() => {
        // 内容将在 onQuillReady 中设置
      });
    },
    onQuillReady(msg, quillInstance) {
      this.quillRefs[msg.id] = quillInstance;

      // 只在编辑器准备好时设置内容
      if (msg.isEditing) {
        // 使用 setTimeout 确保 DOM 完全渲染
        setTimeout(() => {
          // 如果 tempText 为空，使用 originalText
          const content = msg.tempText || msg.originalText || '';
          quillInstance.clipboard.dangerouslyPasteHTML(0, content);

          // 将设置的内容同步到 tempText
          msg.tempText = quillInstance.root.innerHTML;
        }, 0);
      }
    },
    saveEdit(msg) {
      try {
        const quillInstance = this.quillRefs[msg.id];
        if (quillInstance) {
          msg.text = quillInstance.root.innerHTML;
        }

        msg.isEditing = false;
        this.isEditingMode = false;

        // 清理 Quill 实例
        if (this.quillRefs[msg.id]) {
          delete this.quillRefs[msg.id];
        }

        this.currentEditingId = null;
        this.saveChatSessions();
      } catch (error) {
        console.error('保存编辑内容时出错:', error);
        // 回退到原始文本
        msg.text = msg.originalText || msg.text;
        this.cancelEdit(msg);
      }
    },
    // 退出编辑（不强制恢复旧内容；如需“取消恢复”，可在进入编辑时缓存 originalText）
    cancelEdit(msg) {
      msg.isEditing = false;
      msg.tempText = msg.originalText || msg.text;
      this.isEditingMode = false;

      // 清理 Quill 实例
      if (this.quillRefs[msg.id]) {
        delete this.quillRefs[msg.id];
      }

      this.currentEditingId = null;
      this.saveChatSessions();
    },

    // --- 基础工具 ---
    filteredMessages(sender) {
      return this.messages.filter(msg => msg.sender === sender);
    },

    adjustTextareaHeight(textarea) {
      if (!textarea) return;
      textarea.style.height = 'auto';
      textarea.style.height = `${Math.min(textarea.scrollHeight, 150)}px`;
    },

    formatTime(date = new Date()) {
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    },

    // --- 会话管理 ---
    async deleteSession(sessionId) {
      if (!confirm('确定要删除此聊天会话吗？此操作不可撤销。')) return;
      try {
        await axios.delete(`${this.apiBaseUrl}/sessions/${sessionId}`);
      } catch (e) {
        console.error('删除会话后端失败(忽略以保证本地可用):', e);
      }

      this.chatSessions = this.chatSessions.filter(s => s.id !== sessionId);
      if (this.currentSessionId === sessionId) {
        if (this.chatSessions.length > 0) {
          this.currentSessionId = this.chatSessions[0].id;
          this.loadCurrentSession();
        } else {
          this.initDefaultSession();
        }
      }
      this.saveChatSessions();
      this.scrollToBottom();
    },

    async createNewSession() {
      const id = uuidv4();
      const s = { id, title: `聊天 ${this.chatSessions.length + 1}`, messages: [], createdAt: new Date().toISOString() };
      this.chatSessions.unshift(s);
      this.currentSessionId = id;
      this.messages = [];
      this.saveChatSessions();
    },

    async switchChat(id) {
      if (this.currentSessionId === id) return;
      this.currentSessionId = id;
      this.loadCurrentSession();
      this.scrollToBottom();
    },

    initDefaultSession() {
      this.chatSessions = [{ id: 'default-session', title: '默认聊天', messages: [] }];
      this.currentSessionId = 'default-session';
      this.messages = [];
      this.saveChatSessions();
    },

    loadCurrentSession() {
      const s = this.chatSessions.find(ss => ss.id === this.currentSessionId);
      this.messages = s ? s.messages : [];
    },

    saveChatSessions() {
      try { localStorage.setItem('chatSessions', JSON.stringify(this.chatSessions)); }
      catch (e) { console.error('save chat sessions failed', e); }
    },

    updateMessageContent(id, newContent) {
      const session = this.chatSessions.find(s => s.id === this.currentSessionId);
      if (!session) return;
      const messageToUpdate = session.messages.find(msg => msg.id === id);
      if (messageToUpdate) {
        messageToUpdate.text = newContent;
        this.saveChatSessions();
      }
    },

    // --- 消息发送 / 删除 / 清空 ---
    async sendMessage() {
      if (this.inputMessage.trim() === '' || this.isSending) return;

      this.isSending = true;
      const userPrompt = this.inputMessage;

      const selectedFormat = this.selectedOptions.format || 'default';
      let formatInstructions = '';
      switch (selectedFormat) {
        case 'report':
          formatInstructions = `
        请按照正式的报告格式返回HTML。
        大标题请使用 h1 标签，并添加内联样式 style="font-size: 24px; text-align: center; font-weight: bold;"。
        小标题使用 h2 标签，并添加内联样式 style="font-size: 18px; font-weight: bold;"。
        **所有段落请使用 <p> 标签，并添加内联样式 style="font-size: 14px; line-height: 1.8; text-indent: 2em;"。**
        **请确保在每个标题和段落之间都有换行。**
        请确保内容整体字体为宋体。
    `;
          break;
        case 'essay':
          formatInstructions = `
        请按照论文的格式返回HTML。
        大标题请使用 h1 标签，并添加内联样式 style="font-size: 20px; font-weight: bold;"。
        小标题使用 h2 标签，并添加内联样式 style="font-size: 16px; font-weight: bold;"。
        **所有段落请使用 <p> 标签，并添加内联样式 style="font-size: 14px; line-height: 1.6;"。**
        **请确保在每个标题和段落之间都有换行。**
        请确保内容整体字体为宋体。
    `;
          break;
        case 'example':
          formatInstructions = `
        请以举例的格式返回HTML。
        标题请使用 h1 标签，并添加内联样式 style="font-size: 28px; text-align: center;"。
        内容使用 p 标签，并添加内联样式 style="font-size: 16px; margin-bottom: 20px;"。
        请使用粗体<b>来强调重点。
        请确保内容整体字体为宋体。
    `;
          break;
        default:
          formatInstructions = `
        请用HTML格式返回，使用<h1>、<h2>、<p>、<strong>、<ul>、<li>等标签进行排版。
        并确保大标题（h1）居中显示且字体较大，小标题（h2）加粗。
        所有正文和标题的字体都应为宋体。
        确保语言通顺，没有错别字。
    `;
          break;
      }
      const finalPrompt = `请根据以下要求和内容进行写作和排版，直接输出最终内容，不要添加任何额外说明或客套话：
内容：${userPrompt}
要求：${formatInstructions}`;

      const userMessage = {
        id: uuidv4(),
        role: 'user',
        sender: 'user',
        text: userPrompt,
        time: this.formatTime(),
        isEditing: false
      };

      const session = this.chatSessions.find(s => s.id === this.currentSessionId);
      if (session) {
        session.messages.push(userMessage);
        this.messages = session.messages;
        if (session.messages.length === 1) {
          session.title = userPrompt.substring(0, 20) + (userPrompt.length > 20 ? '...' : '');
        }
      }
      this.inputMessage = '';
      this.scrollToBottom();

      const aiResponse = {
        id: uuidv4(),
        role: 'assistant',
        sender: 'ai',
        text: '思考中...',
        time: this.formatTime(),
        isEditing: false
      };
      if (session) session.messages.push(aiResponse);
      this.saveChatSessions();
      this.scrollToBottom();
      aiResponse.text = '';
      try {
        const response = await fetch(`${this.apiBaseUrl}/sessions/${this.currentSessionId}/messages`, {
          method: 'POST',
          headers: {'Content-Type': 'application/json'},
          body: JSON.stringify({prompt: finalPrompt}),
        });

        if (!response.ok) throw new Error(`网络错误: ${response.status}`);

        const reader = response.body.getReader();
        const decoder = new TextDecoder('utf-8');

        let done = false;
        let accumulatedText = '';
        let tagBuffer = '';

        while (!done) {
          const {value, done: readerDone} = await reader.read();
          done = readerDone;

          if (value) {
            const chunk = decoder.decode(value, {stream: true});

            // ✅ 关键修改：处理 SSE 前缀和换行符
            const cleanedChunk = chunk
                .split('\n')
                .map(line => line.startsWith('data:') ? line.substring(5).trim() : line)
                .join('');
            let cleanedText = cleanedChunk.replace(/<[^>]+?>/g, ''); // 移除所有看起来像标签的乱码
            cleanedText = cleanedText.replace(/<0民查p/g, '<p>');
            cleanedText = '<p>' + cleanedText.replace(/\n\s*\n/g, '</p><p>') + '</p>';

            accumulatedText += cleanedChunk;

            let newHtmlToDisplay = '';
            let remainingText = accumulatedText;

            while (true) {
              const openTagIndex = remainingText.indexOf('<');
              const closeTagIndex = remainingText.indexOf('>');

              if (openTagIndex === -1 && closeTagIndex === -1) {
                newHtmlToDisplay += remainingText;
                remainingText = '';
                break;
              }

              if (openTagIndex !== -1 && (openTagIndex < closeTagIndex || closeTagIndex === -1)) {
                newHtmlToDisplay += remainingText.substring(0, openTagIndex + 1);
                remainingText = remainingText.substring(openTagIndex + 1);
              } else if (closeTagIndex !== -1) {
                newHtmlToDisplay += remainingText.substring(0, closeTagIndex + 1);
                remainingText = remainingText.substring(closeTagIndex + 1);
              } else {
                tagBuffer = remainingText;
                remainingText = '';
                break;
              }
            }

            // 将处理后的 HTML 更新到消息文本
            aiResponse.text += newHtmlToDisplay;
            this.$forceUpdate();
            this.scrollToBottom();

            // 将剩余文本（不完整的标签）存入下一次循环
            accumulatedText = remainingText;
          }
        }

        // ✅ 传输结束后，处理缓冲区中可能残留的文本
        if (tagBuffer.length > 0) {
          aiResponse.text += tagBuffer;
        }

        this.saveChatSessions();
        this.$forceUpdate();
        this.scrollToBottom();

      } catch (error) {
        console.error('流式请求失败:', error);
        aiResponse.text = `请求失败: ${error.message}。`;
        try {
          const r = await axios.post(`${this.apiBaseUrl}/sessions/${this.currentSessionId}/messages`, {prompt: finalPrompt});
          if (r.data) {
            aiResponse.text = r.data;
            this.saveChatSessions();
            this.$forceUpdate();
            this.scrollToBottom();
          }
        } catch (fallbackError) {
          console.error('普通请求也失败:', fallbackError);
          aiResponse.text = `所有请求方式都失败了: ${fallbackError.message}`;
        }
      } finally {
        this.isSending = false;
        this.saveChatSessions();
        this.clearSelections();
      }
    },

    async deleteMessage(id) {
      if (!confirm('确定要删除这条消息吗？')) return;

      const session = this.chatSessions.find(s => s.id === this.currentSessionId);
      if (!session) return;

      try {
        await axios.delete(`${this.apiBaseUrl}/sessions/${this.currentSessionId}/messages/${id}`);
      } catch (e) {
        console.error('删除消息后端失败(忽略以保证本地可用):', e);
      }

      const idx = session.messages.findIndex(m => m.id === id);
      if (idx !== -1) {
        if (session.messages[idx].sender === 'user' &&
            idx + 1 < session.messages.length &&
            session.messages[idx + 1].sender === 'ai') {
          session.messages.splice(idx, 2);
        } else {
          session.messages.splice(idx, 1);
        }
        this.messages = session.messages;
        this.saveChatSessions();
      }
      this.scrollToBottom();
    },

    async clearChat() {
      if (!confirm('确定要清空当前聊天记录吗？此操作不可撤销。')) return;

      const session = this.chatSessions.find(s => s.id === this.currentSessionId);
      if (!session) return;

      try {
        await axios.delete(`${this.apiBaseUrl}/sessions/${this.currentSessionId}/messages`);
      } catch (e) {
        console.error('清空聊天记录后端失败(忽略以保证本地可用):', e);
      }

      session.messages = [];
      this.messages = [];
      this.saveChatSessions();
    },

    // --- 用户消息编辑（纯文本） ---
    startEdit(message) {
      this.editingMessageId = message.id;
      this.editingMessageText = message.text;
      this.$nextTick(() => {
        const editInputRef = this.$refs.editInput?.[0];
        if (editInputRef) {
          editInputRef.focus();
          this.adjustTextareaHeight(editInputRef);
        }
      });
    },

    async confirmEdit() {
      if (!this.editingMessageText.trim() || this.isSending) {
        this.cancelEdit();
        return;
      }

      this.isSending = true;
      const msg = this.messages.find(m => m.id === this.editingMessageId);
      if (!msg) { this.isSending = false; return; }
      const idx = this.messages.findIndex(m => m.id === msg.id);
      msg.text = this.editingMessageText;
      msg.time = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
      this.cancelEditUser();

      // 删除紧随其后的旧 AI 回复（若存在）
      if (idx !== -1 && idx + 1 < this.messages.length && this.messages[idx + 1].sender === 'ai') {
        this.messages.splice(idx + 1, 1);
      }
      this.saveChatSessions();

      try {
        const response = await axios.post(this.apiBaseUrl, { prompt: msg.text });
        const newAi = { id: uuidv4(), role: 'assistant', sender: 'ai', text: response.data, time: this.formatTime() };
        this.messages.splice(idx + 1, 0, newAi);
        this.saveChatSessions();
      } catch (e) {
        console.error('confirmEdit API error', e);
        this.messages.push({ id: uuidv4(), role: 'assistant', sender: 'ai', text: `请求失败: ${e.message}`, time: this.formatTime() });
      } finally {
        this.isSending = false;

      }
    },

    cancelEditUser() {
      this.editingMessageId = null;
      this.editingMessageText = '';
    },
    // --- 悬浮面板 / 选区 ---
    handleAIMessageSelection(event, msg) {
      const selection = window.getSelection();
      const selectedText = selection.toString().trim();
      this.activeMessage = msg;
      if (!selectedText) {
        this.showFloatingPanel = false;
        this.selectedRange = null;
        return;
      }

      const range = selection.getRangeAt(0);
      const currentElement = event.currentTarget;
      if (!currentElement.contains(range.commonAncestorContainer)) {
        this.showFloatingPanel = false;
        this.selectedRange = null;
        return;
      }
      this.selectedText = selectedText;
      this.selectedRange = range; // 保存选区对象
      const rect = range.getBoundingClientRect();

      const parentRect = this.$refs.documentContainer.getBoundingClientRect();
      let panelTop = event.clientY + 10;
      let panelLeft = event.clientX;

      const panelWidth = 320;
      if (panelLeft + panelWidth > window.innerWidth) {
        panelLeft = window.innerWidth - panelWidth - 20; // 留一点边距
      }
      if (panelTop < 0) {
        panelTop = 10;
      }

      this.panelStyle = {
        top: `${panelTop}px`,
        left: `${panelLeft}px`
      };

      this.selectedText = selectedText;

      this.activeMessage = msg;
      this.showFloatingPanel = true;
    },

    async regenerateAndReplace(msg, newPrompt) {
      if (!this.selectedRange || this.isSending) return;

      this.isSending = true;
      this.showFloatingPanel = false;

      const originalRange = this.selectedRange;
      const placeholder = document.createElement('span');
      placeholder.textContent = '生成中...';
      originalRange.deleteContents();
      originalRange.insertNode(placeholder);

      let newContent = '';
      try {
        const response = await fetch(`${this.apiBaseUrl}/sessions/${this.currentSessionId}/messages`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ prompt: newPrompt }),
        });

        if (!response.ok) throw new Error(`网络错误: ${response.status}`);

        const reader = response.body.getReader();
        const decoder = new TextDecoder('utf-8');

        while (true) {
          const { value, done } = await reader.read();
          if (done) break;

          const chunk = decoder.decode(value, { stream: true });
          // ✅ 关键修复：处理 data: 前缀和空格，并将内容拼接成一个完整的字符串
          const lines = chunk.split('\n');
          const filteredLines = lines.filter(line => line.startsWith('data:'));

          // 移除前缀并拼接内容，同时移除多余的空格
          const cleanedChunk = filteredLines.map(line => line.substring(5).trim()).join('');

          newContent += cleanedChunk;
          placeholder.innerHTML = newContent;
          this.$forceUpdate();
        }

        // 传输完成后，将更新后的内容存回消息对象
        msg.text = this.$refs.documentContainer.querySelector(`.document-content > div:not(.ai-editor-container)`).innerHTML;

      } catch (error) {
        console.error('局部重新生成失败:', error);
        placeholder.innerHTML = `生成失败: ${error.message}`;
      } finally {
        this.isSending = false;
        this.selectedRange = null;
        this.saveChatSessions();
      }
    },


    handleUserMessageSelection() {
      const selection = window.getSelection();
      const selectedText = selection.toString().trim();
      if (!selectedText) {
        this.showFloatingPanel = false;
        return;
      }

      const range = selection.getRangeAt(0);
      const parentContainer = this.$refs.userMessagePanel;
      if (parentContainer && parentContainer.contains(range.startContainer)) {
        const rect = range.getBoundingClientRect();

        let panelTop = rect.bottom + window.scrollY + 5;
        let panelLeft = rect.left + window.scrollX;

        const panelWidth = 320;
        if (panelLeft + panelWidth > window.innerWidth) {
          panelLeft = window.innerWidth - panelWidth - 10;
        }

        this.panelStyle = { top: `${panelTop}px`, left: `${panelLeft}px` };
        this.selectedText = selectedText;
        this.showFloatingPanel = true;
      } else {
        this.showFloatingPanel = false;
      }
    },

    handleDocumentClick(event) {
      const panelElement = this.$refs.floatingPanel;
      if (!panelElement) return;
      if (panelElement.contains(event.target)) return;
      if (this.$refs.customPromptTextarea && this.$refs.customPromptTextarea === document.activeElement) return;
      this.showFloatingPanel = false;
    },

    applyPreset(prompt) {
      if (!this.activeMessage) return;

      if (this.selectedRange) {
        // 情况一：局部重新生成
        const newPrompt = `根据这段文本："${this.selectedText}"。新要求是：${prompt}`;
        this.regenerateAndReplace(this.activeMessage, newPrompt);
      } else {
        // 情况二：整篇重新生成（保持你原来的逻辑）
        const newPrompt = `请根据此内容重新生成一段文字: ${this.activeMessage.text}。新要求是：${prompt}`;
        this.regenerate(this.activeMessage, newPrompt);
      }

      this.showFloatingPanel = false;
      this.selectedText = '';
      this.customPrompt = '';
    },

    regenerateWithCustomPrompt() {
      if (!this.customPrompt.trim() || !this.activeMessage) return;

      if (this.selectedRange) {
        // 局部重新生成
        const newPrompt = `根据这段文本："${this.selectedText}"。新要求是：${this.customPrompt.trim()}`;
        this.regenerateAndReplace(this.activeMessage, newPrompt);
      } else {
        // 整篇重新生成
        const newPrompt = `请根据此内容重新生成一段文字: ${this.activeMessage.text}。新要求是：${this.customPrompt.trim()}`;
        this.regenerate(this.activeMessage, newPrompt);
      }

      this.showFloatingPanel = false;
      this.selectedText = '';
      this.customPrompt = '';
    },

    async regenerate(msg, newPrompt) {
      if (!msg || this.isSending) return;

      this.isSending = true;

      // 创建一个消息占位符
      const tempMsg = {
        id: uuidv4(),
        role: 'assistant',
        sender: 'ai',
        text: '重新生成中...',
        time: this.formatTime(),
        isEditing: false
      };

      // 找到原始消息的索引，并替换它
      const originalIndex = this.messages.findIndex(m => m.id === msg.id);
      if (originalIndex !== -1) {
        this.messages.splice(originalIndex, 1, tempMsg);
      }

      this.saveChatSessions();
      this.scrollToBottom();
      let accumulatedText = '';
      try {
        const response = await fetch(`${this.apiBaseUrl}/sessions/${this.currentSessionId}/messages`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ prompt: newPrompt }),
        });

        if (!response.ok) throw new Error(`网络错误: ${response.status}`);

        // 流式处理
        const reader = response.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let accumulatedText = '';

        while (true) {
          const { value, done } = await reader.read();
          if (done) break;

          const chunk = decoder.decode(value, { stream: true });

          // ✅ 关键修复：处理 data: 前缀和空格，并将内容拼接成一个完整的字符串
          const lines = chunk.split('\n');
          const filteredLines = lines.filter(line => line.startsWith('data:'));
          const cleanedChunk = filteredLines.map(line => line.substring(5).trim()).join('');

          accumulatedText += cleanedChunk;
          tempMsg.text = accumulatedText;
          this.$forceUpdate();
          this.scrollToBottom();
        }

      } catch (error) {
        console.error('重新生成请求失败:', error);
        tempMsg.text = `重新生成失败: ${error.message}`;
      } finally {
        this.isSending = false;
        this.saveChatSessions();
      }
    },
    // --- 提示词生成 ---
    selectOption(categoryKey, optionId) {
      this.selectedOptions[categoryKey] =
          this.selectedOptions[categoryKey] === optionId ? null : optionId;
    },

    clearSelections() {
      this.selectedOptions.theme = null;
      this.selectedOptions.tone = null;
      this.selectedOptions.length = null;
    },

    generateFromSuggestions() {
      let themeText = null, toneText = null, lengthText = null;

      for (const key in this.selectedOptions) {
        const selectedId = this.selectedOptions[key];
        if (selectedId) {
          const category = this.promptCategories[key];
          const selectedOption = category.options.find(opt => opt.id === selectedId);
          if (selectedOption) {
            if (key === 'theme') themeText = selectedOption.text;
            if (key === 'tone') toneText = selectedOption.text;
            if (key === 'length') lengthText = selectedOption.text;
          }
        }
      }

      if (!themeText) return;

      let combinedPrompt = `请生成一篇文案，主题是"${themeText}"。`;
      if (toneText) combinedPrompt += `请使用"${toneText}"的语气来撰写。`;
      if (lengthText) combinedPrompt += `要求文案为"${lengthText}"。`;
      combinedPrompt += '要求语言通顺，无错别字。';

      this.inputMessage = combinedPrompt;
      this.sendMessage();
      this.clearSelections();
    },

    // --- 视图 ---
    scrollToBottom() {
      this.$nextTick(() => {
        const docContainer = this.$refs.documentContainer;
        const userContainer = this.$refs.userMessagePanel;
        if (docContainer) docContainer.scrollTop = docContainer.scrollHeight;
        if (userContainer) userContainer.scrollTop = userContainer.scrollHeight;
      });
    },
  },

  computed: {
    isGenerateEnabled() {
      return Object.values(this.selectedOptions).some(id => id !== null);
    }
  }
};
</script>

<style>
/* 导入 Quills Editor 的样式 */
@import '@vueup/vue-quill/dist/vue-quill.snow.css';
@import "../assets/chat.css";
</style>