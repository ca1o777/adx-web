package br.com.adx.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.adx.dto.DadosCadastro;
import br.com.adx.dto.DadosLogin;
import br.com.adx.dto.DadosUsuario;
import br.com.adx.seguranca.CookieDeSessao;
import br.com.adx.service.AutenticacaoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AutenticacaoController {

    private final AutenticacaoService servico;

    public AutenticacaoController(AutenticacaoService servico) {
        this.servico = servico;
    }

    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public DadosUsuario cadastrar(@RequestBody @Valid DadosCadastro dados) {
        return servico.cadastrar(dados);
    }

    @PostMapping("/login")
    public ResponseEntity<DadosUsuario> entrar(@RequestBody @Valid DadosLogin dados, HttpServletRequest request) {
        AutenticacaoService.Login login = servico.entrar(dados);
        // quem entra de novo no mesmo navegador troca de sessão; a anterior deixa de valer
        CookieDeSessao.ler(request).ifPresent(servico::sair);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, CookieDeSessao.criar(login.token(), request.isSecure()).toString())
                .body(login.usuario());
    }

    /** Encerra a sessão deste navegador. Responde 204 mesmo sem sessão válida, e sempre apaga o cookie. */
    @PostMapping("/logout")
    public ResponseEntity<Void> sair(HttpServletRequest request) {
        CookieDeSessao.ler(request).ifPresent(servico::sair);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, CookieDeSessao.apagar(request.isSecure()).toString())
                .build();
    }
}
