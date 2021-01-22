package com.kevin.key;

import scala.math.Ordered;

import java.io.Serializable;

public class UserCityPackageKey implements Ordered<UserCityPackageKey>, Serializable
{
  public Long userId;
  public String city;

  @Override
  public int hashCode() {
    int result = userId != null ? userId.hashCode() : 0;
    result = 31 * result + (city != null ? city.hashCode() : 0);
    return result;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public Long getUserId() {
    return userId;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public int compareTo(UserCityPackageKey that)
  {
    if (this.userId == that.getUserId()) {
        return this.city.compareTo(that.getCity());
    } else {
      Long n = this.userId - that.getUserId();
      return n > 0 ? 1 : (n == 0 ? 0 : -1);
    }
  }

  public int compare(UserCityPackageKey that)
  {
    return this.compareTo(that);
  }

  public boolean $greater(UserCityPackageKey that)
  {
    if (this.compareTo(that) > 0) {
      return true;
    }

    return false;
  }

  public boolean $less(UserCityPackageKey that)
  {
    if (this.compareTo(that) < 0) {
      return true;
    }

    return false;
  }

  public boolean $less$eq(UserCityPackageKey that)
  {
    if (this.compareTo(that) <= 0) {
      return true;
    }

    return false;
  }

  public boolean $greater$eq(UserCityPackageKey that) {
    if (this.compareTo(that) >= 0) {
      return true;
    }

    return false;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    UserCityPackageKey that = (UserCityPackageKey) o;

    if (userId != null ? !userId.equals(that.userId) : that.userId != null) return false;
    return !(city != null ? !city.equals(that.city) : that.city != null);

  }

  @Override
  public String toString() {
    return "UserHourPackageKey{" +
            "userId=" + userId +
            ", city=" + city + '\'' +
            '}';
  }
}
