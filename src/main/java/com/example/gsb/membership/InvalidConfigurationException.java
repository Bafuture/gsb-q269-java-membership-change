package com.example.gsb.membership;

/** 配置本身非法（如成员列表为空）时抛出。 */
public class InvalidConfigurationException extends MembershipException {

    public InvalidConfigurationException(String message) {
        super(message);
    }
}
