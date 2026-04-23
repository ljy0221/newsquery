package com.newsquery.service;

import com.newsquery.domain.KeywordSubscription;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class KeywordSubscriptionService {

    private final Map<String, KeywordSubscription> subscriptions = new ConcurrentHashMap<>();

    public KeywordSubscription subscribe(String userId, String keyword) {
        KeywordSubscription subscription = new KeywordSubscription(userId, keyword);
        subscriptions.put(subscription.getId(), subscription);
        return subscription;
    }

    public boolean unsubscribe(String subscriptionId) {
        return subscriptions.remove(subscriptionId) != null;
    }

    public List<String> getSubscribedKeywords(String userId) {
        return subscriptions.values().stream()
            .filter(sub -> sub.getUserId().equals(userId) && sub.isActive())
            .map(KeywordSubscription::getKeyword)
            .collect(Collectors.toList());
    }

    public List<KeywordSubscription> getSubscriptions(String userId) {
        return subscriptions.values().stream()
            .filter(sub -> sub.getUserId().equals(userId) && sub.isActive())
            .collect(Collectors.toList());
    }
}
