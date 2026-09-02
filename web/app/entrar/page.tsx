'use client';

import { useState } from 'react';
import { conta, guardarSessao } from '@/lib/api';

export default function Entrar() {
  const [modo, setModo] = useState<'entrar' | 'cadastrar'>('entrar');
  const [nome, setNome] = useState('');
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [erro, setErro] = useState<string | null>(null);
  const [processando, setProcessando] = useState(false);

  async function enviar(evento: React.FormEvent) {
    evento.preventDefault();
    setProcessando(true);
    setErro(null);
    try {
      const sessao =
        modo === 'entrar'
          ? await conta.entrar(email, senha)
          : await conta.cadastrar(nome, email, senha);
      guardarSessao(sessao);
      const voltar = new URLSearchParams(window.location.search).get('voltar');
      window.location.href = voltar ?? '/';
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setProcessando(false);
    }
  }

  return (
    <section className="secao">
      <div className="envolucro" style={{ maxWidth: 430 }}>
        <h2 className="titulo-secao">{modo === 'entrar' ? 'Entrar' : 'Criar conta'}</h2>

        <form className="cartao" onSubmit={(e) => void enviar(e)}>
          {modo === 'cadastrar' && (
            <div className="campo">
              <label htmlFor="nome">Nome</label>
              <input
                id="nome"
                value={nome}
                onChange={(e) => setNome(e.target.value)}
                autoComplete="name"
                required
              />
            </div>
          )}

          <div className="campo">
            <label htmlFor="email">E-mail</label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="email"
              required
            />
          </div>

          <div className="campo">
            <label htmlFor="senha">Senha</label>
            <input
              id="senha"
              type="password"
              value={senha}
              onChange={(e) => setSenha(e.target.value)}
              autoComplete={modo === 'entrar' ? 'current-password' : 'new-password'}
              minLength={modo === 'cadastrar' ? 10 : undefined}
              required
            />
            {modo === 'cadastrar' && (
              <span className="apagado">Pelo menos 10 caracteres.</span>
            )}
          </div>

          {erro && <div className="aviso erro">{erro}</div>}

          <button className="botao largo" type="submit" disabled={processando}>
            {processando ? 'Aguarde...' : modo === 'entrar' ? 'Entrar' : 'Criar conta'}
          </button>

          <p className="apagado" style={{ textAlign: 'center', marginBottom: 0, marginTop: 16 }}>
            {modo === 'entrar' ? 'Ainda nao tem conta? ' : 'Ja tem conta? '}
            <button
              type="button"
              onClick={() => {
                setModo(modo === 'entrar' ? 'cadastrar' : 'entrar');
                setErro(null);
              }}
              style={{
                background: 'none',
                border: 'none',
                color: 'var(--ambar)',
                cursor: 'pointer',
                padding: 0,
              }}
            >
              {modo === 'entrar' ? 'criar agora' : 'entrar'}
            </button>
          </p>
        </form>

        <div className="aviso" style={{ marginTop: 20 }}>
          <strong>Contas da demonstracao</strong>
          <div className="mono" style={{ marginTop: 8, lineHeight: 1.8 }}>
            espectador@exemplo.com
            <br />
            dono@cineserra.com.br
            <br />
            plataforma@outorga.app
            <br />
            <span className="fraco">senha de todas: demonstracao2026</span>
          </div>
        </div>
      </div>
    </section>
  );
}
