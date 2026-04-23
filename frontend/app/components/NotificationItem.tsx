'use client';

import React from 'react';
import { SSENotification } from '../hooks/useSSE';
import styles from './NotificationItem.module.css';

interface Props {
    notification: SSENotification;
}

/**
 * 알림 항목 컴포넌트
 *
 * 각 알림을 타입별로 다르게 표시합니다:
 * - PERFORMANCE_ALERT: ⚡ (주황색)
 * - ERROR_ALERT: ❌ (빨강색)
 * - KEYWORD_ALERT: 🔑 (파랑색)
 */
const NotificationItem: React.FC<Props> = ({ notification }) => {
    const getIcon = (type: string): string => {
        switch (type) {
            case 'PERFORMANCE_ALERT':
                return '⚡';
            case 'ERROR_ALERT':
                return '❌';
            case 'KEYWORD_ALERT':
                return '🔑';
            default:
                return 'ℹ️';
        }
    };

    const getClassName = (type: string): string => {
        const base = styles.notificationItem;
        const typeClass = type
            .toLowerCase()
            .replace(/_/g, '-');
        return `${base} ${styles[`notification-${typeClass}`]}`;
    };

    const formatTime = (timestamp?: number): string => {
        if (!timestamp) return new Date().toLocaleTimeString('ko-KR');

        const date = new Date(timestamp);
        return date.toLocaleTimeString('ko-KR');
    };

    const truncateMessage = (message: string, maxLength: number = 100): string => {
        return message.length > maxLength ? `${message.substring(0, maxLength)}...` : message;
    };

    return (
        <div className={getClassName(notification.type)}>
            {/* 헤더 */}
            <div className={styles.header}>
                <span className={styles.icon}>{getIcon(notification.type)}</span>
                <span className={styles.type}>{notification.type}</span>
                <span className={styles.time}>{formatTime(notification.timestamp)}</span>
            </div>

            {/* 메시지 */}
            <div className={styles.body}>
                <p className={styles.message}>
                    {truncateMessage(notification.message)}
                </p>

                {/* 상세 정보 (있을 경우) */}
                {notification.details && Object.keys(notification.details).length > 0 && (
                    <details className={styles.details}>
                        <summary>상세 정보</summary>
                        <pre className={styles.detailsContent}>
                            {JSON.stringify(notification.details, null, 2)}
                        </pre>
                    </details>
                )}
            </div>

            {/* 상태 표시 */}
            {notification.read && (
                <div className={styles.readBadge}>읽음</div>
            )}
        </div>
    );
};

export default NotificationItem;
