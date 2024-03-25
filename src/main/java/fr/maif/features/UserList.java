package fr.maif.features;

import java.util.Set;

public class UserList implements ActivationRule {
    public Set<String> users;

    public UserList(Set<String> users) {
        this.users = users;
    }

    public boolean active(String user, String featureId){
        return users.contains(user);
    }
}
