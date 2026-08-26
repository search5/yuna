package com.github.search5.yona.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import org.springframework.security.web.savedrequest.SavedRequest
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import com.github.search5.yona.config.git.GitAuthorizationFilter
import com.github.search5.yona.config.oauth2.CustomOAuth2UserService
import com.github.search5.yona.config.svn.SvnAuthorizationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val customOAuth2UserService: CustomOAuth2UserService,
    private val gitAuthorizationFilter: GitAuthorizationFilter,
    private val svnAuthorizationFilter: SvnAuthorizationFilter,
    private val apiTokenAuthenticationFilter: ApiTokenAuthenticationFilter,
    private val accessLogFilter: AccessLogFilter
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { csrf -> csrf.disable() }
            .headers { headers ->
                headers.frameOptions { frameOptions ->
                    frameOptions.sameOrigin()
                }
            }
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers("/css/**", "/js/**", "/images/**", "/stylesheets/**", "/javascripts/**", "/bootstrap/**", "/assets/**").permitAll()
                    .requestMatchers("/login", "/signup", "/lostPassword", "/user/reset-password", "/bootstrap-setup", "/users/loginform", "/users/signupform", "/users/signup").permitAll()
                    .requestMatchers("/git/**").permitAll()
                    .requestMatchers("/svn/**").permitAll()
                    .requestMatchers("/site/**", "/sites/**").hasAnyRole("ADMIN", "SITE_ADMIN")
                    .anyRequest().permitAll()
            }
            .formLogin { form ->
                form
                    .loginPage("/users/loginform")
                    .loginProcessingUrl("/users/login")
                    .usernameParameter("loginIdOrEmail")
                    .passwordParameter("password")
                    .successHandler(YonaAuthenticationSuccessHandler())
                    .failureHandler(YonaAuthenticationFailureHandler())
                    .permitAll()
            }
            .rememberMe { rememberMe ->
                rememberMe
                    .rememberMeParameter("rememberMe")
                    .key("yonaRememberMeKey")
            }
            .httpBasic { }
            .oauth2Login { oauth2 ->
                oauth2
                    .loginPage("/users/loginform")
                    .userInfoEndpoint { userInfo ->
                        userInfo.userService(customOAuth2UserService)
                    }
                    .defaultSuccessUrl("/")
            }
            .logout { logout ->
                logout
                    .logoutUrl("/users/logout")
                    .logoutSuccessUrl("/users/loginform?logout")
                    .permitAll()
            }
            .addFilterAfter(gitAuthorizationFilter, BasicAuthenticationFilter::class.java)
            .addFilterAfter(svnAuthorizationFilter, BasicAuthenticationFilter::class.java)
            .addFilterAfter(apiTokenAuthenticationFilter, BasicAuthenticationFilter::class.java)
            .addFilterAfter(accessLogFilter, BasicAuthenticationFilter::class.java)
        return http.build()
    }
}

class YonaAuthenticationSuccessHandler : AuthenticationSuccessHandler {
    private val requestCache = HttpSessionRequestCache()

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val requestedWith = request.getHeader("X-Requested-With")
        val accept = request.getHeader("Accept")
        val isAjax = "XMLHttpRequest" == requestedWith || (accept != null && accept.contains("application/json"))

        if (isAjax) {
            response.contentType = "application/json;charset=UTF-8"
            response.status = HttpServletResponse.SC_OK
            response.writer.write("{}")
            response.writer.flush()
        } else {
            val savedRequest: SavedRequest? = requestCache.getRequest(request, response)
            val targetUrl = savedRequest?.redirectUrl ?: "/"
            response.sendRedirect(targetUrl)
        }
    }
}

class YonaAuthenticationFailureHandler : AuthenticationFailureHandler {

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException
    ) {
        val requestedWith = request.getHeader("X-Requested-With")
        val accept = request.getHeader("Accept")
        val isAjax = "XMLHttpRequest" == requestedWith || (accept != null && accept.contains("application/json"))

        if (isAjax) {
            response.contentType = "application/json;charset=UTF-8"
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.writer.write("{\"message\":\"user.login.invalid\"}")
            response.writer.flush()
        } else {
            response.sendRedirect("/users/loginform?error=true")
        }
    }
}

