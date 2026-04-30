"use client";

// TODO: For maintainability, consider refactoring this file into smaller components:
// - ChatHeader
// - Conversation
// - Composer
// - EmailPrompt
// - ErrorPopup
// Utility functions can be moved to a separate utils file.

import { useEffect, useState } from "react";

const STORAGE_KEYS = {
  identity: "chatbot.identity",
  sessionId: "chatbot.sessionId",
};

const API_BASE = process.env.NEXT_PUBLIC_CHATBOT_API_BASE || "/api/chatbot";
const ATTACHMENT_API = "/api/attachments";
const EMPTY_STATUS = "Enter your email to continue.";
const ATTACHMENT_MARKER_START = "\n\n<<<ATTACHMENTS>>>\n";
const ATTACHMENT_MARKER_END = "\n<<<END_ATTACHMENTS>>>";
const MAX_CHAT_CONTENT = 8000;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// Allowed file types and max size for attachments
const ALLOWED_FILE_TYPES = [
  "application/pdf",
  "text/plain",
  "application/msword",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "image/png",
  "image/jpeg",
  // Add more as needed
];
const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
const MAX_ATTACHMENTS = 5;

function formatBytes(size) {
  if (!size) {
    return "0 B";
  }

  const units = ["B", "KB", "MB", "GB"];
  const index = Math.min(Math.floor(Math.log(size) / Math.log(1024)), units.length - 1);
  const value = size / 1024 ** index;
  return `${value.toFixed(value >= 10 || index === 0 ? 0 : 1)} ${units[index]}`;
}

function sortSessions(list) {
  return [...list].sort((left, right) => {
    const leftTime = new Date(left.lastActiveAt || left.createdAt || 0).getTime();
    const rightTime = new Date(right.lastActiveAt || right.createdAt || 0).getTime();
    return rightTime - leftTime;
  });
}

function formatTimestamp(value) {
  if (!value) {
    return "";
  }

  return new Intl.DateTimeFormat("en-IN", {
    hour: "2-digit",
    minute: "2-digit",
    month: "short",
    day: "numeric",
  }).format(new Date(value));
}

function isValidEmail(value) {
  return EMAIL_PATTERN.test(value.trim());
}

function isSessionMissingError(message) {
  return /session.*not found|has been deleted/i.test(message || "");
}

function normalizeAttachment(attachment) {
  return {
    id: attachment.id,
    name: attachment.name,
    size: attachment.size,
    type: attachment.type,
    textExtracted: Boolean(attachment.textExtracted),
    excerpt: attachment.excerpt || "",
  };
}

function buildAttachmentContext(attachments) {
  return attachments
    .map((attachment, index) => {
      const lines = [
        `Attachment ${index + 1}`,
        `Name: ${attachment.name}`,
        `Type: ${attachment.type || "application/octet-stream"}`,
        `Size: ${formatBytes(attachment.size)}`,
      ];

      if (attachment.textExtracted && attachment.excerpt) {
        lines.push("Text excerpt:");
        lines.push(attachment.excerpt);
      } else {
        lines.push("Text excerpt: Not available for this file type.");
      }

      return lines.join("\n");
    })
    .join("\n\n");
}

function buildMessagePayload(content, attachments) {
  const prompt = content.trim().slice(0, MAX_CHAT_CONTENT);

  if (!attachments.length) {
    return { content: prompt, truncated: false };
  }

  const contextPrefix = "The user uploaded the following attachments as context.\n\n";
  const rawContext = `${contextPrefix}${buildAttachmentContext(attachments)}`;
  const availableContextLength =
    MAX_CHAT_CONTENT - prompt.length - ATTACHMENT_MARKER_START.length - ATTACHMENT_MARKER_END.length;

  if (availableContextLength <= 0) {
    return {
      content: prompt,
      truncated: true,
    };
  }

  const truncated = rawContext.length > availableContextLength;
  const safeContext = truncated
    ? `${rawContext.slice(0, Math.max(0, availableContextLength - 32)).trimEnd()}\n[Attachment context truncated]`
    : rawContext;

  return {
    content: `${prompt}${ATTACHMENT_MARKER_START}${safeContext}${ATTACHMENT_MARKER_END}`,
    truncated,
  };
}

function parseMessageContent(content) {
  if (typeof content !== "string") {
    return { content: "", attachments: [] };
  }

  const markerIndex = content.indexOf(ATTACHMENT_MARKER_START);

  if (markerIndex === -1) {
    return { content, attachments: [] };
  }

  const endIndex = content.indexOf(ATTACHMENT_MARKER_END, markerIndex);
  const visibleContent = content.slice(0, markerIndex).trimEnd();
  const attachmentBlock = content.slice(
    markerIndex + ATTACHMENT_MARKER_START.length,
    endIndex === -1 ? content.length : endIndex
  );

  const attachments = Array.from(attachmentBlock.matchAll(/^Name:\s*(.+)$/gm)).map((match, index) => ({
    id: `history-${index}-${match[1]}`,
    name: match[1].trim(),
  }));

  return {
    content: visibleContent,
    attachments,
  };
}

function normalizeMessage(message) {
  const parsed = parseMessageContent(message.content);

  return {
    ...message,
    content: parsed.content,
    attachments: parsed.attachments,
  };
}

async function apiRequest(path, options = {}) {
  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      ...options,
      headers: {
        "Content-Type": "application/json",
        ...(options.headers || {}),
      },
      cache: "no-store",
    });
  } catch (networkError) {
    throw new Error("Network error. Please check your connection or try again later.");
  }

  let payload = null;
  try {
    payload = await response.json();
  } catch {
    payload = null;
  }

  if (!response.ok || !payload?.success) {
    throw new Error(payload?.message || `Request failed with status ${response.status}.`);
  }

  return payload.data;
}

async function createSession(identity) {
  return apiRequest("/sessions", {
    method: "POST",
    body: JSON.stringify({ identity }),
  });
}

async function getSessions(identity) {
  return apiRequest(`/sessions?identity=${encodeURIComponent(identity)}`);
}

async function getHistory(sessionId) {
  return apiRequest(`/sessions/${sessionId}/history`);
}

async function sendChatMessage(sessionId, content) {
  return apiRequest(`/sessions/${sessionId}/chat`, {
    method: "POST",
    body: JSON.stringify({ content }),
  });
}

async function uploadAttachments(files) {
  const formData = new FormData();

  files.forEach((file) => {
    formData.append("files", file);
  });

  const response = await fetch(ATTACHMENT_API, {
    method: "POST",
    body: formData,
  });

  let payload = null;

  try {
    payload = await response.json();
  } catch {
    payload = null;
  }

  if (!response.ok || !payload?.success) {
    throw new Error(payload?.message || "Attachment upload failed.");
  }

  return payload.data.map(normalizeAttachment);
}

export default function Page() {
  const [hasHydrated, setHasHydrated] = useState(false);
  const [theme, setTheme] = useState('light');
      // Theme persistence and switching
      useEffect(() => {
        const savedTheme = window.localStorage.getItem('theme') || 'light';
        setTheme(savedTheme);
        document.documentElement.setAttribute('data-theme', savedTheme);
      }, []);

      function toggleTheme() {
        const nextTheme = theme === 'light' ? 'dark' : 'light';
        setTheme(nextTheme);
        window.localStorage.setItem('theme', nextTheme);
        document.documentElement.setAttribute('data-theme', nextTheme);
      }
  const [identity, setIdentity] = useState("");
  const [emailDraft, setEmailDraft] = useState("");
  const [storedSessionId, setStoredSessionId] = useState("");
  const [activeSessionId, setActiveSessionId] = useState("");
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState("");
  const [attachments, setAttachments] = useState([]);
  const [status, setStatus] = useState(EMPTY_STATUS);
  const [error, setError] = useState("");
  const [lastModel, setLastModel] = useState("");
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [uploadingAttachments, setUploadingAttachments] = useState(false);
  const [sending, setSending] = useState(false);
  const [bootstrapping, setBootstrapping] = useState(false);
  const [showEmailPrompt, setShowEmailPrompt] = useState(true);
  const [popupError, setPopupError] = useState("");

  // Show error in both inline and popup
  function raiseError(message) {
    setError(message);
    setPopupError(message);
  }

  useEffect(() => {
    const savedIdentity = window.localStorage.getItem(STORAGE_KEYS.identity) || "";
    const savedSessionId = window.localStorage.getItem(STORAGE_KEYS.sessionId) || "";

    setIdentity(savedIdentity);
    setEmailDraft(savedIdentity);
    setStoredSessionId(savedSessionId);
    setStatus(savedIdentity ? "Confirm your email to continue." : EMPTY_STATUS);
    setShowEmailPrompt(true);
    setHasHydrated(true);
  }, []);

  useEffect(() => {
    if (!hasHydrated) {
      return;
    }

    if (identity) {
      window.localStorage.setItem(STORAGE_KEYS.identity, identity);
    } else {
      window.localStorage.removeItem(STORAGE_KEYS.identity);
    }
  }, [hasHydrated, identity]);

  useEffect(() => {
    if (!hasHydrated) {
      return;
    }

    if (activeSessionId) {
      window.localStorage.setItem(STORAGE_KEYS.sessionId, activeSessionId);
      setStoredSessionId(activeSessionId);
    } else {
      window.localStorage.removeItem(STORAGE_KEYS.sessionId);
      setStoredSessionId("");
    }
  }, [activeSessionId, hasHydrated]);

  async function resolveSessionId(identityValue, preferredSessionId = "") {
    const sessions = sortSessions(await getSessions(identityValue));

    if (preferredSessionId && sessions.some((session) => session.sessionId === preferredSessionId)) {
      return { sessionId: preferredSessionId, isNew: false };
    }

    if (sessions.length) {
      return { sessionId: sessions[0].sessionId, isNew: false };
    }

    const session = await createSession(identityValue);
    return { sessionId: session.sessionId, isNew: true };
  }

  async function startConversation(nextIdentity, preferredSessionId = "") {
    const trimmedIdentity = nextIdentity.trim();

    if (!isValidEmail(trimmedIdentity)) {
      raiseError("Enter a valid email address.");
      setStatus("Waiting for a valid email.");
      return false;
    }

    setBootstrapping(true);
    setError("");
    setPopupError("");

    try {
      const { sessionId, isNew } = await resolveSessionId(trimmedIdentity, preferredSessionId);

      setIdentity(trimmedIdentity);
      setEmailDraft(trimmedIdentity);
      setActiveSessionId(sessionId);
      setMessages([]);
      setAttachments([]);
      setDraft("");
      setLastModel("");
      setShowEmailPrompt(false);
      setStatus(isNew ? "Email confirmed. A new conversation is ready." : "Email confirmed. Restoring your conversation.");
      return true;
    } catch (requestError) {
      raiseError(requestError.message);
      setStatus("Unable to start the conversation.");
      return false;
    } finally {
      setBootstrapping(false);
    }
  }

  useEffect(() => {
    if (!activeSessionId || showEmailPrompt) {
      if (!activeSessionId) {
        setMessages([]);
      }
      return;
    }

    let cancelled = false;

    async function loadHistory() {
      setLoadingHistory(true);
      setError("");

      try {
        const history = await getHistory(activeSessionId);

        if (!cancelled) {
          setMessages(history.map(normalizeMessage));
          setStatus(history.length ? "Conversation history loaded." : "Conversation ready. Ask your first question.");
        }
      } catch (requestError) {
        if (cancelled) {
          return;
        }

        if (identity.trim() && isSessionMissingError(requestError.message)) {
          try {
            const recovered = await resolveSessionId(identity.trim());

            if (!cancelled) {
              setMessages([]);
              setActiveSessionId(recovered.sessionId);
              setStatus("Your previous conversation expired. A fresh conversation is ready.");
            }
            return;
          } catch (recoveryError) {
            if (!cancelled) {
              raiseError(recoveryError.message);
              setStatus("Conversation recovery failed.");
            }
            return;
          }
        }

        if (!cancelled) {
          raiseError(requestError.message);
          setStatus("Conversation history could not be loaded.");
        }
      } finally {
        if (!cancelled) {
          setLoadingHistory(false);
        }
      }
    }

    loadHistory();

    return () => {
      cancelled = true;
    };
  }, [activeSessionId, identity, showEmailPrompt]);

  async function handleEmailSubmit(event) {
    event.preventDefault();

    const trimmedEmail = emailDraft.trim();
    const preferredSessionId = trimmedEmail === identity.trim() ? activeSessionId || storedSessionId : "";

    await startConversation(trimmedEmail, preferredSessionId);
  }

  function handleEmailPromptClose() {
    if (!identity) {
      return;
    }

    setEmailDraft(identity);
    setError("");
    setPopupError("");
    setShowEmailPrompt(false);
    setStatus(messages.length ? "Conversation ready." : "Continue where you left off.");
  }

  async function handleAttachmentSelection(event) {
    const files = Array.from(event.target.files || []);
    event.target.value = "";

    if (!files.length) {
      return;
    }

    // Client-side validation
    if (attachments.length + files.length > MAX_ATTACHMENTS) {
      raiseError(`You can upload up to ${MAX_ATTACHMENTS} files per message.`);
      setStatus("Attachment limit exceeded.");
      return;
    }
    for (const file of files) {
      if (!ALLOWED_FILE_TYPES.includes(file.type)) {
        raiseError(`File type not allowed: ${file.name}`);
        setStatus("Unsupported file type.");
        return;
      }
      if (file.size > MAX_FILE_SIZE) {
        raiseError(`File too large: ${file.name} (max 10 MB)`);
        setStatus("File size limit exceeded.");
        return;
      }
    }

    setUploadingAttachments(true);
    setError("");
    setPopupError("");

    try {
      const uploaded = await uploadAttachments(files);
      setAttachments((current) => [...current, ...uploaded]);
      setStatus(
        `${uploaded.length} attachment${uploaded.length === 1 ? '' : 's'} ready for the next message.`
      );
    } catch (requestError) {
      raiseError(requestError.message);
      setStatus("Attachment upload failed.");
    } finally {
      setUploadingAttachments(false);
    }
  }

  function removeAttachment(attachmentId) {
    setAttachments((current) => current.filter((attachment) => attachment.id !== attachmentId));
  }

  async function handleSendMessage(event) {
    event.preventDefault();

    const trimmedIdentity = identity.trim();
    const trimmedDraft = draft.trim();

    if (!trimmedIdentity) {
      setShowEmailPrompt(true);
      raiseError("Email is required before sending a message.");
      setStatus("Waiting for email confirmation.");
      return;
    }

    if (!trimmedDraft && !attachments.length) {
      return;
    }

    setSending(true);
    setError("");
    setPopupError("");

    const messageText = trimmedDraft || "Use the uploaded attachments as the prompt context.";
    const selectedAttachments = attachments;
    const payload = buildMessagePayload(messageText, selectedAttachments);
    const optimisticId = `pending-${Date.now()}`;

    try {
      let sessionId = activeSessionId;

      if (!sessionId) {
        const resolved = await resolveSessionId(trimmedIdentity, storedSessionId);
        sessionId = resolved.sessionId;
        setActiveSessionId(sessionId);
      }

      const optimisticUserMessage = {
        role: "USER",
        content: messageText,
        attachments: selectedAttachments,
        timestamp: new Date().toISOString(),
        localId: optimisticId,
      };

      setMessages((current) => [...current, optimisticUserMessage]);
      setDraft("");
      setAttachments([]);

      let response;
      let recoveredSession = false;

      try {
        response = await sendChatMessage(sessionId, payload.content);
      } catch (requestError) {
        if (!isSessionMissingError(requestError.message)) {
          throw requestError;
        }

        const recovered = await resolveSessionId(trimmedIdentity);
        sessionId = recovered.sessionId;
        recoveredSession = true;
        setActiveSessionId(sessionId);
        response = await sendChatMessage(sessionId, payload.content);
      }

      setMessages((current) => [
        ...current.map((message) =>
          message.localId === optimisticId ? { ...message, localId: undefined } : message
        ),
        {
          role: "ASSISTANT",
          content: response.assistantMessage,
          timestamp: response.timestamp,
          attachments: [],
        },
      ]);

      setLastModel(response.model || "");
      setStatus(
        payload.truncated
          ? `Reply generated with ${response.model || "the configured model"}. Attachment context was trimmed to fit the backend limit.`
          : recoveredSession
            ? `Conversation recovered. Reply generated with ${response.model || "the configured model"}.`
            : `Reply generated with ${response.model || "the configured model"}.`
      );
    } catch (requestError) {
      setMessages((current) => current.filter((message) => message.localId !== optimisticId));
      setDraft(trimmedDraft);
      setAttachments(selectedAttachments);
      raiseError(requestError.message);
      setStatus("Message delivery failed.");
    } finally {
      setSending(false);
    }
  }

  function handleComposerKeyDown(event) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();

      if (sending || uploadingAttachments || bootstrapping || (!draft.trim() && !attachments.length)) {
        return;
      }

      event.currentTarget.form?.requestSubmit();
    }
  }

  const isBusy = loadingHistory || uploadingAttachments || sending || bootstrapping;
  const queuedAttachmentsLabel = attachments.length
    ? `${attachments.length} queued`
    : "No files queued";

  return (
    <main className="shell">
      <div className="ambient ambientOne" />
      <div className="ambient ambientTwo" />

      <section className="workspace singleColumnWorkspace">
        <section className="chatStage chatStageWithStickyComposer">
          <header className="chatHeader modernHeader" style={{background:'#f7fafc',borderBottom:'1px solid #e2e8f0',boxShadow:'0 2px 8px rgba(16,163,127,0.03)'}}>
            <div className="headerFlex" style={{alignItems:'center',gap:24}}>
              <span aria-label="Chatbot Logo" className="logoContainer">
                {/* SVG Logo unchanged */}
                <svg
                  width="54" height="54" viewBox="0 0 54 54" fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                  className="logoBounce"
                >
                  <defs>
                    <radialGradient id="logoGradient" cx="50%" cy="50%" r="50%">
                      <stop offset="0%" stopColor="#19c37d" />
                      <stop offset="100%" stopColor="#10a37f" />
                    </radialGradient>
                  </defs>
                  <circle cx="27" cy="27" r="24" fill="url(#logoGradient)" stroke="#10a37f" strokeWidth="3" />
                  <ellipse cx="27" cy="34" rx="11" ry="7" fill="#fff" opacity="0.7" />
                  <ellipse cx="19.5" cy="23" rx="2.7" ry="3.7" fill="#fff" />
                  <ellipse cx="34.5" cy="23" rx="2.7" ry="3.7" fill="#fff" />
                  <ellipse cx="19.5" cy="23" rx="1.3" ry="1.8" fill="#19c37d" />
                  <ellipse cx="34.5" cy="23" rx="1.3" ry="1.8" fill="#19c37d" />
                  <path d="M21 32c1.7 2.2 9.3 2.2 11 0" stroke="#fff" strokeWidth="2.2" strokeLinecap="round" />
                  <circle cx="19" cy="22" r="0.7" fill="#19c37d" />
                  <circle cx="35" cy="22" r="0.7" fill="#19c37d" />
                  <ellipse cx="27" cy="44" rx="2.5" ry="1.2" fill="#fff" opacity="0.5">
                    <animate attributeName="rx" values="2.5;4;2.5" dur="1.8s" repeatCount="indefinite" />
                  </ellipse>
                </svg>
              </span>
              <h1 className="pageTitle" style={{margin:0,fontSize:'1.5em',fontWeight:600,color:'#111'}}>
                beck
                <span className="modelLabel" style={{fontSize:'0.6em',fontWeight:400,color:'#666',marginLeft:8}}>
                  {lastModel ? `· ${lastModel}` : ""}
                </span>
              </h1>
            </div>
          </header>

          {hasHydrated ? (
            <div className="chatContentWrapper">
              <div className="chatContent" style={{padding:24,flexGrow:1,overflowY:'auto',scrollBehavior:'smooth'}}>
                <div className="messageList" style={{display:'flex',flexDirection:'column',gap:16}}>
                  {messages.length ? (
                    messages.map((message, index) => {
                      const isUser = message.role === "USER";
                      const isAssistant = message.role === "ASSISTANT";
                      const isFirst = index === 0;
                      const isLast = index === messages.length - 1;

                      return (
                        <div
                          key={message.timestamp}
                          className={`messageWrapper ${isUser ? 'userMessage' : ''} ${isAssistant ? 'assistantMessage' : ''}`}
                          style={{
                            alignSelf: isUser ? 'flex-end' : 'flex-start',
                            maxWidth: '80%',
                            position: 'relative',
                            marginBottom: isLast ? 0 : 16,
                          }}
                        >
                          <div
                            className="messageBubble"
                            style={{
                              background: isUser ? '#dcf8c6' : '#fff',
                              borderRadius: isUser ? '16px 16px 0 16px' : '16px 16px 16px 0',
                              padding: '12px 16px',
                              border: isAssistant ? '1px solid #e2e8f0' : 'none',
                              boxShadow: isAssistant ? '0 2px 8px rgba(16,163,127,0.1)' : 'none',
                            }}
                          >
                            {message.attachments && message.attachments.length > 0 ? (
                              <div className="attachmentPreview" style={{ marginBottom: 8 }}>
                                {message.attachments.map((attachment) => (
                                  <div key={attachment.id} className="attachmentItem" style={{ marginRight: 8, display: 'inline-block' }}>
                                    <div className="attachmentInfo" style={{ fontSize: '0.9em', color: '#333' }}>
                                      <strong>{attachment.name}</strong>
                                      <span style={{ marginLeft: 4, fontWeight: 500 }}>
                                        {formatBytes(attachment.size)}
                                        {attachment.textExtracted ? " · text ready" : " · binary"}
                                      </span>
                                    </div>
                                    <div className="attachmentActions" style={{ marginTop: 4 }}>
                                      <button
                                        type="button"
                                        className="downloadButton"
                                        onClick={() => window.open(`${API_BASE}${ATTACHMENT_API}/${attachment.id}`, "_blank")}
                                        style={{
                                          background: 'none',
                                          border: 'none',
                                          color: '#10a37f',
                                          fontSize: '0.9em',
                                          cursor: 'pointer',
                                          marginRight: 8,
                                        }}
                                      >
                                        Download
                                      </button>
                                      <button
                                        type="button"
                                        className="removeButton"
                                        onClick={() => removeAttachment(attachment.id)}
                                        style={{
                                          background: 'none',
                                          border: 'none',
                                          color: '#e55353',
                                          fontSize: '0.9em',
                                          cursor: 'pointer',
                                        }}
                                      >
                                        Remove
                                      </button>
                                    </div>
                                  </div>
                                ))}
                              </div>
                            ) : null}
                            <div className="messageText" style={{ whiteSpace: 'pre-wrap', wordWrap: 'break-word', color: '#111' }}>
                              {message.content}
                            </div>
                          </div>
                          <div className="messageTimestamp" style={{
                            position: 'absolute',
                            bottom: -18,
                            right: 0,
                            fontSize: '0.8em',
                            color: '#999',
                            whiteSpace: 'nowrap',
                          }}>
                            {formatTimestamp(message.timestamp)}
                          </div>
                        </div>
                      );
                    })
                  ) : (
                    <div className="emptyState" style={{ textAlign: 'center', color: '#666', padding: '40px 0' }}>
                      <p style={{ fontSize: '1.2em', margin: 0 }}>No messages yet.</p>
                      <p style={{ fontSize: '0.9em', marginTop: 8 }}>
                        Ask a question or upload a file to get started.
                      </p>
                    </div>
                  )}
                </div>
              </div>
            </div>
          ) : null}

          <div className="stickyComposerWrapper">
            <form className="composer modernComposer stickyComposer" onSubmit={handleSendMessage} autoComplete="off">
              <textarea
                id="message"
                className="composerInput modernComposerInput"
                value={draft}
                maxLength={8000}
                onChange={(event) => setDraft(event.target.value)}
                onKeyDown={handleComposerKeyDown}
                aria-label="Message"
                placeholder="Type your question here, or attach files for context..."
                disabled={showEmailPrompt || bootstrapping}
              />
              <div className="attachmentToolbar modernAttachmentToolbar">
                <label
                  className={`secondaryButton uploadButton uploadButtonIcon modernUploadButton${showEmailPrompt || bootstrapping ? " isDisabled" : ""}`}
                  htmlFor="attachmentInput"
                  aria-label="Attach files"
                >
                  <span aria-hidden="true">{uploadingAttachments ? "⋯" : "📎"}</span>
                  <span className="srOnly">{uploadingAttachments ? "Uploading files" : "Attach files"}</span>
                </label>
                <input
                  id="attachmentInput"
                  className="hiddenInput"
                  type="file"
                  multiple
                  disabled={showEmailPrompt || bootstrapping}
                  onChange={handleAttachmentSelection}
                />
                <span className="attachmentHint modernAttachmentHint">
                  Up to 5 files. Text-based files are folded into the prompt automatically.
                </span>
              </div>
              {attachments.length ? (
                <div className="attachmentList modernAttachmentList">
                  {attachments.map((attachment) => (
                    <div key={attachment.id} className="attachmentChip modernAttachmentChip">
                      <div className="attachmentCopy modernAttachmentCopy">
                        <strong>{attachment.name}</strong>
                        <span>
                          {formatBytes(attachment.size)}
                          {attachment.textExtracted ? " · text ready" : " · binary"}
                        </span>
                      </div>
                      <button
                        type="button"
                        className="removeAttachmentButton modernRemoveAttachmentButton"
                        onClick={() => removeAttachment(attachment.id)}
                        aria-label={`Remove ${attachment.name}`}
                      >
                        ×
                      </button>
                    </div>
                  ))}
                </div>
              ) : null}
              <div className="composerFooter modernComposerFooter">
                <span>
                  {draft.trim().length}/8000
                  {attachments.length ? ` · ${attachments.length} attachment${attachments.length === 1 ? "" : "s"}` : ""}
                </span>
                <button
                  type="submit"
                  className="primaryButton modernPrimaryButton"
                  disabled={
                    sending ||
                    uploadingAttachments ||
                    bootstrapping ||
                    showEmailPrompt ||
                    (!draft.trim() && !attachments.length)
                  }
                  aria-busy={sending}
                >
                  {sending ? "Submitting..." : "Submit"}
                </button>
              </div>
            </form>
          </div>
        </section>
      </section>

      {/* Email Prompt Modal */}
      {showEmailPrompt ? (
        <div
          className="modalScrim modernModalScrim"
          tabIndex={-1}
          aria-modal="true"
          role="dialog"
          aria-labelledby="emailPromptTitle"
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            width: '100vw',
            height: '100vh',
            background: 'rgba(0,0,0,0.32)',
            zIndex: 1000,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
          onClick={() => {
            setShowEmailPrompt(false);
            setError("");
            setPopupError("");
          }}
          onKeyDown={e => {
            if (e.key === 'Escape') {
              setShowEmailPrompt(false);
              setError("");
              setPopupError("");
            }
          }}
        >
          <section
            className="emailModal modernEmailModal"
            role="document"
            style={{
              background: '#fff',
              borderRadius: 24,
              boxShadow: '0 8px 32px rgba(16,163,127,0.13)',
              padding: 32,
              minWidth: 340,
              maxWidth: '90vw',
              margin: 'auto',
              position: 'relative',
            }}
            onClick={e => e.stopPropagation()}
          >
            <button
              type="button"
              aria-label="Close"
              style={{
                position: 'absolute',
                top: 16,
                right: 16,
                background: 'none',
                border: 'none',
                fontSize: 24,
                color: '#b3c6f7',
                cursor: 'pointer',
              }}
              onClick={() => {
                setShowEmailPrompt(false);
                setError("");
                setPopupError("");
              }}
            >
              ×
            </button>
            <p className="eyebrow">Continue</p>
            <h2 id="emailPromptTitle">Enter your email</h2>
            <p className="modalCopy">
              We use your email to restore an existing chat or create a new one automatically.
            </p>
            <form className="modalForm modernModalForm" onSubmit={handleEmailSubmit} autoComplete="off">
              <label className="fieldLabel modernFieldLabel" htmlFor="emailPrompt">
                Email
              </label>
              <input
                id="emailPrompt"
                className="textInput modernTextInput"
                type="email"
                value={emailDraft}
                onChange={(event) => setEmailDraft(event.target.value)}
                placeholder="name@example.com"
                autoComplete="email"
                autoFocus
                aria-required="true"
              />
              {error && (
                <div style={{color:'#e55353',margin:'8px 0',fontWeight:600,fontSize:'1em'}}>{error}</div>
              )}
              <div className="modalActions modernModalActions">
                {activeSessionId ? (
                  <button type="button" className="ghostButton modernGhostButton" onClick={handleEmailPromptClose}>
                    Cancel
                  </button>
                ) : null}
                <button type="submit" className="primaryButton modernPrimaryButton" disabled={bootstrapping} aria-busy={bootstrapping}>
                  {bootstrapping ? "Continuing..." : "Continue"}
                </button>
                <button
                  type="button"
                  className="ghostButton modernGhostButton"
                  style={{ marginLeft: 8, fontWeight: 700 }}
                  onClick={() => {
                    setShowEmailPrompt(false);
                    setError("");
                    setPopupError("");
                  }}
                  aria-label="Continue without email prompt"
                >
                  Continue
                </button>
              </div>
            </form>
          </section>
        </div>
      ) : null}
      {/* Error Popup Modal */}
      {popupError ? (
        <div
          className="popupScrim modernPopupScrim"
          role="presentation"
          tabIndex={-1}
          aria-modal="true"
          onClick={() => setPopupError("")}
          onKeyDown={(e) => {
            if (e.key === "Escape") setPopupError("");
          }}
        >
          <section
            className="errorPopup modernErrorPopup"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="errorPopupTitle"
            tabIndex={0}
            onClick={(event) => event.stopPropagation()}
          >
            <div className="errorPopupHeader modernErrorPopupHeader">
              <div>
                <p className="eyebrow">Error</p>
                <h2 id="errorPopupTitle">Something went wrong</h2>
              </div>
              <button type="button" className="ghostButton popupCloseButton modernPopupCloseButton" onClick={() => setPopupError("")} aria-label="Close error popup">
                ×
              </button>
            </div>
            <p className="popupCopy modernPopupCopy">{popupError}</p>
          </section>
        </div>
      ) : null}
    </main>
  );
}
