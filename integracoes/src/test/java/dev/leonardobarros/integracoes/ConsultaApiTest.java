package dev.leonardobarros.integracoes;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.apache.camel.test.spring.junit6.CamelSpringBootTest;
import org.apache.camel.test.spring.junit6.UseAdviceWith;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import dev.leonardobarros.integracoes.notificacao.Notificacao;
import dev.leonardobarros.integracoes.notificacao.NotificacoesRecentes;

/** A API de consulta: exige o JWT do motor e mostra só o que é da conta do token. */
@CamelSpringBootTest
@UseAdviceWith   // as rotas não sobem: aqui só interessa a camada HTTP
@SpringBootTest(properties = "camel.springboot.main-run-controller=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfig.class)
class ConsultaApiTest {

    @Autowired MockMvc mvc;
    @Autowired NotificacoesRecentes notificacoes;

    @Test
    void semTokenNaoEntra() throws Exception {
        mvc.perform(get("/api/notificacoes")).andExpect(status().isUnauthorized());
        // health continua público (aqui as rotas não sobem, então só o liveness fica UP)
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }

    @Test
    void cadaContaSoVeAsProprias() throws Exception {
        UUID minha = UUID.randomUUID();
        UUID deOutro = UUID.randomUUID();
        UUID transferencia = UUID.randomUUID();
        notificacoes.registrar(new Notificacao(UUID.randomUUID(), minha, "eu@motor.dev", "Pix enviado", "...",
                transferencia, Instant.now()));
        notificacoes.registrar(new Notificacao(UUID.randomUUID(), deOutro, "outro@motor.dev", "Pix recebido", "...",
                transferencia, Instant.now()));

        mvc.perform(get("/api/notificacoes").with(jwt().jwt(token -> token.claim("account_id", minha.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].titulo").value("Pix enviado"));
    }
}
