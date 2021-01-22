package com.kevin.model;

public class UserCityStatModel extends BaseModel
{
  private String userId;
  private String city;

  public Long getNum() {
    return num;
  }

  public void setNum(Long num) {
    this.num = num;
  }

  private Long num;

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getUserId() {
    return userId;
  }

}
