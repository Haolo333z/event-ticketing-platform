import { createElement as h, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from '../api/client.ts';
import type { Api, AiSeatSuggestion, User } from '../api/client.ts';

type ChatBubble = {
  id: string;
  sender: 'USER' | 'ASSISTANT';
  text: string;
  adapter?: string;
  suggestions?: AiSeatSuggestion[];
};

const vnd = (v: number) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(v);

export function AiConciergeWidget({
  api,
  user,
  onBooked,
  onRequireAuth
}: {
  api: Api;
  user: User | null;
  onBooked?: (bookingId: string) => void;
  onRequireAuth?: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [conversationId, setConversationId] = useState<string | undefined>(undefined);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [holdingKey, setHoldingKey] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatBubble[]>([
    {
      id: 'welcome',
      sender: 'ASSISTANT',
      text: 'Xin chào! Mình là Trợ lý AI Săn Vé EventFlow. Bạn muốn tìm cụm ghế liền kề theo ngân sách hay tra cứu đơn hàng & hoàn tiền?',
      adapter: 'HEXAGONAL_AI'
    }
  ]);

  async function sendPrompt(text: string) {
    const trimmed = text.trim();
    if (!trimmed || busy || !api.ai?.conciergeChat) return;
    setBusy(true);
    setInput('');
    setMessages(prev => [...prev, { id: 'u-' + Date.now(), sender: 'USER', text: trimmed }]);
    try {
      const res = await api.ai.conciergeChat({ conversationId, message: trimmed });
      setConversationId(res.conversationId);
      setMessages(prev => [
        ...prev,
        {
          id: 'a-' + Date.now(),
          sender: 'ASSISTANT',
          text: res.reply,
          adapter: res.adapterUsed,
          suggestions: res.suggestions
        }
      ]);
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Trợ lý AI tạm thời bận, vui lòng thử lại.';
      setMessages(prev => [...prev, { id: 'e-' + Date.now(), sender: 'ASSISTANT', text: '⚠️ ' + msg }]);
    } finally {
      setBusy(false);
    }
  }

  async function holdSuggestedSeats(card: AiSeatSuggestion) {
    if (holdingKey) return;
    if (!user && onRequireAuth) {
      onRequireAuth();
      return;
    }
    const cardKey = card.eventId + ':' + card.ticketTypeId;
    setHoldingKey(cardKey);
    try {
      const attempt = {
        key: crypto.randomUUID(),
        payload: card.mode === 'SEATED'
          ? { eventId: card.eventId, ticketTypeId: card.ticketTypeId, seatIds: [...card.seatIds].sort() }
          : { eventId: card.eventId, ticketTypeId: card.ticketTypeId, quantity: card.quantity }
      };
      const booking = await api.create(attempt);
      setMessages(prev => [
        ...prev,
        {
          id: 'b-' + Date.now(),
          sender: 'ASSISTANT',
          text: '✅ Đã khóa chỗ ACID thành công (Mã đơn #' + booking.bookingId.slice(0, 8) + ', tổng ' + vnd(booking.totalAmountVnd) + '). Đang mở màn hình thanh toán giữ chỗ 10 phút!'
        }
      ]);
      if (onBooked) onBooked(booking.bookingId);
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Không thể giữ chỗ lúc này.';
      setMessages(prev => [...prev, { id: 'be-' + Date.now(), sender: 'ASSISTANT', text: '❌ ' + msg }]);
    } finally {
      setHoldingKey(null);
    }
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    void sendPrompt(input);
  }

  return h('div', { className: 'ai-concierge-root' },
    !open && h('button', {
      type: 'button',
      className: 'ai-fab-btn',
      'aria-label': 'Mở Trợ lý AI Săn Vé EventFlow',
      onClick: () => setOpen(true)
    }, '🤖 Trợ lý AI'),

    open && h('aside', { className: 'ai-drawer', 'aria-label': 'Trợ lý AI Săn Vé EventFlow' },
      h('div', { className: 'ai-drawer-header' },
        h('div', null,
          h('strong', null, '🤖 EventFlow AI Concierge'),
          h('small', null, 'Tìm ghế liền kề & tra cứu đơn thời gian thực')
        ),
        h('button', {
          type: 'button',
          className: 'ai-close-btn',
          'aria-label': 'Đóng Trợ lý AI',
          onClick: () => setOpen(false)
        }, '✕')
      ),

      h('div', { className: 'ai-quick-chips' },
        h('button', { type: 'button', onClick: () => void sendPrompt('Tìm 2 ghế liền kề dưới 1 triệu') }, '💺 2 ghế liền kề < 1tr'),
        h('button', { type: 'button', onClick: () => void sendPrompt('Tìm 3 vé sự kiện dưới 2 triệu') }, '🎟️ 3 vé < 2tr'),
        h('button', { type: 'button', onClick: () => void sendPrompt('Kiểm tra đơn hàng và hoàn tiền của tôi') }, '📦 Tra cứu đơn & hoàn tiền')
      ),

      h('div', { className: 'ai-messages-list' },
        ...messages.map(m => h('div', { key: m.id, className: 'ai-bubble ' + (m.sender === 'USER' ? 'user' : 'assistant') },
          h('p', { className: 'ai-bubble-text' }, m.text),
          m.adapter && h('span', { className: 'ai-adapter-tag' }, 'Adapter: ' + m.adapter),
          m.suggestions && m.suggestions.length > 0 && h('div', { className: 'ai-suggestions-list' },
            ...m.suggestions.map(card => {
              const key = card.eventId + ':' + card.ticketTypeId;
              return h('div', { key, className: 'ai-suggestion-card' },
                h('strong', null, card.eventTitle),
                h('div', { className: 'ai-suggestion-meta' },
                  card.ticketTypeName + ' · ' + (card.seatLabels.length ? 'Ghế: ' + card.seatLabels.join(', ') : card.quantity + ' vé GA')
                ),
                h('div', { className: 'ai-suggestion-price' }, 'Tổng: ' + vnd(card.totalPriceVnd)),
                h('button', {
                  type: 'button',
                  className: 'ai-hold-now-btn',
                  disabled: holdingKey !== null,
                  onClick: () => void holdSuggestedSeats(card)
                }, holdingKey === key ? 'Đang khóa chỗ ACID…' : '⚡ Giữ cụm ghế này ngay')
              );
            })
          )
        ))
      ),

      h('form', { className: 'ai-input-form', onSubmit: handleSubmit },
        h('input', {
          type: 'text',
          value: input,
          disabled: busy,
          placeholder: 'Hỏi AI: Tìm 3 ghế cạnh nhau dưới 3 triệu…',
          onChange: (e: { target: { value: string } }) => setInput(e.target.value)
        }),
        h('button', { type: 'submit', disabled: busy || !input.trim() }, busy ? '…' : 'Gửi')
      )
    )
  );
}
