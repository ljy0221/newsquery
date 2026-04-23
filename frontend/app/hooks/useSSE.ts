'use client';

import { useEffect, useState, useCallback } from 'react';

export interface SSENotification {
    id: string;
    type: string;
    message: string;
    details?: Record<string, any>;
    userId?: string;
    createdAt?: string;
    read?: boolean;
    timestamp?: number;
}

export type ConnectionStatus = 'connecting' | 'connected' | 'disconnected';

/**
 * SSE (Server-Sent Events) 구독 훅
 *
 * 특징:
 * - 자동 재연결 (브라우저 자동 처리)
 * - Last-Event-ID를 통한 메시지 손실 복구
 * - 타입안전 (TypeScript)
 * - 정리 자동화 (useEffect cleanup)
 *
 * 사용 예시:
 * ```typescript
 * const { notifications, connectionStatus, error } = useSSE('anonymous');
 *
 * if (connectionStatus === 'connected') {
 *   return <NotificationCenter notifications={notifications} />;
 * }
 * ```
 */
export const useSSE = (userId: string = 'anonymous') => {
    const [notifications, setNotifications] = useState<SSENotification[]>([]);
    const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>('connecting');
    const [error, setError] = useState<string | null>(null);
    const [eventSource, setEventSource] = useState<EventSource | null>(null);

    // 알림 추가 (최근 순서로, 최대 100개 유지)
    const addNotification = useCallback((notification: SSENotification) => {
        setNotifications((prev) => {
            const updated = [notification, ...prev];
            // 최대 100개 유지
            return updated.slice(0, 100);
        });
    }, []);

    // 알림 제거
    const removeNotification = useCallback((id: string) => {
        setNotifications((prev) => prev.filter((n) => n.id !== id));
    }, []);

    // 모든 알림 제거
    const clearNotifications = useCallback(() => {
        setNotifications([]);
    }, []);

    // SSE 연결 해제
    const closeConnection = useCallback(() => {
        if (eventSource) {
            eventSource.close();
            setEventSource(null);
            setConnectionStatus('disconnected');
        }
    }, [eventSource]);

    // SSE 재연결
    const reconnect = useCallback(() => {
        closeConnection();
        // 1초 후 재연결
        setTimeout(() => {
            initializeSSE();
        }, 1000);
    }, [closeConnection]);

    // SSE 초기화
    const initializeSSE = useCallback(() => {
        try {
            setConnectionStatus('connecting');
            setError(null);

            // API URL 구성
            const apiUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
            const url = new URL('/api/notifications/stream', apiUrl);
            url.searchParams.set('user', userId);

            console.log('[SSE] 연결 시도:', url.toString());

            // EventSource 생성
            const es = new EventSource(url.toString(), {
                withCredentials: true,
            });

            setEventSource(es);

            // 1. 연결 열림
            es.onopen = () => {
                setConnectionStatus('connected');
                setError(null);
                console.log('[SSE] 연결 성공');
            };

            // 2. 초기 연결 확인 이벤트
            es.addEventListener('connected', (event: MessageEvent) => {
                try {
                    const data = JSON.parse(event.data);
                    console.log('[SSE] 초기 연결 확인:', data);
                } catch (e) {
                    console.error('[SSE] 초기 이벤트 파싱 실패:', e);
                }
            });

            // 3. 알림 이벤트
            es.addEventListener('notification', (event: MessageEvent) => {
                try {
                    const notification: SSENotification = JSON.parse(event.data);
                    console.log('[SSE] 알림 수신:', notification);
                    addNotification(notification);
                } catch (e) {
                    console.error('[SSE] 알림 파싱 실패:', e);
                    setError('알림 파싱 오류');
                }
            });

            // 4. 에러 처리
            es.onerror = () => {
                console.error('[SSE] 에러 발생:', es.readyState);

                if (es.readyState === EventSource.CONNECTING) {
                    setConnectionStatus('connecting');
                    console.warn('[SSE] 재연결 중...');
                } else if (es.readyState === EventSource.CLOSED) {
                    setConnectionStatus('disconnected');
                    setError('SSE 연결 실패');
                    console.error('[SSE] 연결 종료됨');
                    es.close();
                }
            };

        } catch (err) {
            const errorMessage = err instanceof Error ? err.message : String(err);
            setError(errorMessage);
            setConnectionStatus('disconnected');
            console.error('[SSE] 초기화 실패:', err);
        }
    }, [userId, addNotification]);

    // 마운트/언마운트 처리
    useEffect(() => {
        initializeSSE();

        // 정리: 언마운트 시 연결 해제
        return () => {
            if (eventSource) {
                eventSource.close();
            }
        };
    }, [userId]); // userId 변경 시 재연결

    return {
        notifications,
        connectionStatus,
        error,
        addNotification,
        removeNotification,
        clearNotifications,
        closeConnection,
        reconnect,
    };
};
