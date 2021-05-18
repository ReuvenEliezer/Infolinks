package com.infolinks.entities;

import java.util.ArrayList;
import java.util.List;

public class ClientInfoVariables {
    private List<String> contactList = new ArrayList<>();
    private List<String> subdomainList = new ArrayList<>();

    public List<String> getContactList() {
        return contactList;
    }

    public void setContactList(List<String> contactList) {
        this.contactList = contactList;
    }

    public List<String> getSubdomainList() {
        return subdomainList;
    }

    public void setSubdomainList(List<String> subdomainList) {
        this.subdomainList = subdomainList;
    }

    public void addSubdomain(String subdomain) {
        subdomainList.add(subdomain);
    }

    public void addContact(String contact) {
        contactList.add(contact);
    }

    @Override
    public String toString() {
        return "Variables{" +
                "contactList=" + contactList +
                ", subdomainList=" + subdomainList +
                '}';
    }
}
