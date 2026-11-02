package fr.maif.features;

import java.util.Set;

public class UserOrGroupList implements ActivationRule {
    public Set<String> users;
    public Set<String> groups;

    public UserOrGroupList(Set<String> users, Set<String> groups) {
        this.users = users;
        this.groups = groups;
    }

    public boolean active(String user, String featureId, Set<String> groups){
        return users.contains(user) || this.groups.stream().anyMatch(groups::contains);
    }
}
