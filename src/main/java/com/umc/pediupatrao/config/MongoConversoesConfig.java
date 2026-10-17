package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.StatusPedido;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

/**
 * Leitura tolerante de dados gravados pela versão anterior do sistema
 * (role "USER", status "EM_PREPARO"/"ENTREGUE"), evitando erro ao listar
 * documentos antigos.
 */
@Configuration
public class MongoConversoesConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(new TextoParaPerfil(), new TextoParaStatusPedido()));
    }

    @ReadingConverter
    static class TextoParaPerfil implements Converter<String, Perfil> {
        @Override
        public Perfil convert(String valor) {
            return Perfil.deTexto(valor).orElse(null);
        }
    }

    @ReadingConverter
    static class TextoParaStatusPedido implements Converter<String, StatusPedido> {
        @Override
        public StatusPedido convert(String valor) {
            return StatusPedido.deTexto(valor).orElse(null);
        }
    }
}
