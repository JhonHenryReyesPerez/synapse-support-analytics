package com.roles.usermanagement.domain.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class UserDto {
  private String username;
  private String email;
  @Schema(description = "Nombre de la persona", example = "Ana Gómez")
  private String fullName;
  @Schema(description = "Área o dependencia", example = "Contabilidad")
  private String area;
  private Boolean locked;
  private Boolean disabled;
  @Schema(description = "Indica si el usuario debe cambiar su contraseña obligatoriamente", example = "true")
  private Boolean mustChangePassword; // transports data between client and backend
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private String password;
  /** Uso interno (emisión/validación de tokens); no forma parte del contrato público. */
  @com.fasterxml.jackson.annotation.JsonIgnore
  private Integer credentialVersion;
  @Schema(description = "Rol único del usuario: USER (por defecto), TECHNICIAN o ADMIN.", example = "USER")
  private String role;
  @Schema(description = "Formato anterior compatible: como máximo un rol.", deprecated = true)
  private List<UserRoleDto> roles;
  @Schema(description = "Permisos individuales adicionales al rol")
  private List<String> additionalPermissions;
}
