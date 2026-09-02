package br.com.outorga.application.ports;

/**
 * Cifra e confere senha. O dominio nunca ve senha em texto claro: recebe o
 * hash pronto e o veredito da comparacao.
 */
public interface CifradorDeSenha {

    String cifrar(String senhaEmTextoClaro);

    boolean confere(String senhaEmTextoClaro, String hash);
}
