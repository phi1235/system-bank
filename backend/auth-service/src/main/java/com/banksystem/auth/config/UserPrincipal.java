package com.banksystem.auth.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record UserPrincipal(
    UUID userId,
    String username,
    List<String> roles,
    List<String> permissions
) implements UserDetails {

  public UserPrincipal {
    roles = roles == null ? List.of() : List.copyOf(roles);
    permissions = permissions == null ? List.of() : List.copyOf(permissions);
  }

  public boolean hasPermission(String permission) {
    if (permission == null) {
      return false;
    }
    return permissions.stream().anyMatch(p -> p.equalsIgnoreCase(permission) || "*".equals(p));
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    List<GrantedAuthority> authorities = new ArrayList<>();
    roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
    permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority("PERM_" + p)));
    return authorities;
  }

  @Override
  public String getPassword() {
    return "";
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
