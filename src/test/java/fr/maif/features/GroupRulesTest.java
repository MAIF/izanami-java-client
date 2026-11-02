package fr.maif.features;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class GroupRulesTest {
    @Test
    public void group_rules_should_activate_feature_when_listed_group_is_requested() {
        var rule = new UserOrGroupList(Collections.emptySet(), Collections.singleton("foo"));
        assertThat(rule.active("", "", Collections.singleton("foo"))).isTrue();
    }

    @Test
    public void group_rules_should_not_activate_feature_when_unlisted_group_is_requested() {
        var rule = new UserOrGroupList(Collections.emptySet(), Collections.singleton("foo"));
        assertThat(rule.active("", "", Collections.singleton("bar"))).isFalse();
    }

    @Test
    public void group_rules_should_activate_feature_when_listed_user_is_requested_with_unlisted_group() {
        var rule = new UserOrGroupList(Collections.singleton("u1"), Collections.singleton("foo"));
        assertThat(rule.active("u1", "", Collections.singleton("bar"))).isTrue();
    }

    @Test
    public void group_rules_should_not_activate_feature_when_requested_with_unlisted_user_and_unlisted_group() {
        var rule = new UserOrGroupList(Collections.singleton("u1"), Collections.singleton("foo"));
        assertThat(rule.active("u2", "", Collections.singleton("bar"))).isFalse();
    }
}
