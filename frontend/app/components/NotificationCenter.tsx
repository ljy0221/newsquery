'use client';

import React, { useState } from 'react';
import { useSSE } from '../hooks/useSSE';
import NotificationItem from './NotificationItem';
import styles from './NotificationCenter.module.css';

/**
 * 알림 센터 컴포넌트
 *
 * SSE를 통해 실시간 알림을 수신하고 UI에 표시합니다.
 * - 연결 상태 표시기
 * - 에러 배너
 * - 알림 목록
 * - 알림 개수 배지
 */
export const NotificationCenter: React.FC = () => {
    const { notifications, connectionStatus, error, clearNotifications, reconnect } = useSSE();
    const [collapsed, setCollapsed] = useState(false);

    const unreadCount = notifications.filter((n) => !n.read).length;

    return (
        <div className={styles.notificationCenter}>
            {/* 헤더 */}
            <div className={styles.header}>
                <div className={styles.titleRow}>
                    <h2 className={styles.title}>
                        알림
                        {unreadCount > 0 && (
                            <span className={styles.badge}>{unreadCount}</span>
                        )}
                    </h2>
                    <button
                        className={styles.collapseBtn}
                        onClick={() => setCollapsed(!collapsed)}
                        aria-label="접기/펼치기"
                    >
                        {collapsed ? '▼' : '▲'}
                    </button>
                </div>

                {/* 상태 표시기 */}
                <div className={`${styles.statusIndicator} ${styles[`status-${connectionStatus}`]}`}>
                    <span className={styles.statusDot}></span>
                    <span className={styles.statusText}>
                        {connectionStatus === 'connected' && 'SSE 연결됨'}
                        {connectionStatus === 'connecting' && 'SSE 재연결 중...'}
                        {connectionStatus === 'disconnected' && 'SSE 연결 실패'}
                    </span>
                </div>
            </div>

            {!collapsed && (
                <>
                    {/* 에러 배너 */}
                    {error && (
                        <div className={styles.errorBanner}>
                            <span>{error}</span>
                            <button
                                className={styles.retryBtn}
                                onClick={reconnect}
                            >
                                재연결
                            </button>
                        </div>
                    )}

                    {/* 알림 목록 */}
                    <div className={styles.notificationsList}>
                        {notifications.length === 0 ? (
                            <p className={styles.emptyState}>
                                {connectionStatus === 'connected'
                                    ? '알림이 없습니다'
                                    : 'SSE 연결을 기다리는 중...'}
                            </p>
                        ) : (
                            <>
                                {notifications.length > 0 && (
                                    <button
                                        className={styles.clearBtn}
                                        onClick={clearNotifications}
                                    >
                                        모두 지우기
                                    </button>
                                )}
                                <div className={styles.itemsContainer}>
                                    {notifications.map((notification) => (
                                        <NotificationItem
                                            key={notification.id}
                                            notification={notification}
                                        />
                                    ))}
                                </div>
                            </>
                        )}
                    </div>
                </>
            )}
        </div>
    );
};

export default NotificationCenter;
