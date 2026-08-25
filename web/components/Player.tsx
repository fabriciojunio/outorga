'use client';

import { useEffect, useRef } from 'react';
import Hls from 'hls.js';
import { reproducao } from '@/lib/api';

/**
 * Player HLS.
 *
 * Duas coisas aqui nao sao detalhe. O sinal de vida a cada trinta segundos e
 * o que sustenta o limite de telas do plano: sem ele, uma sessao que caiu
 * continuaria ocupando vaga. E o encerramento ao sair da pagina devolve a
 * vaga na hora, em vez de esperar a tolerancia estourar.
 *
 * Safari toca HLS nativo e nao precisa da biblioteca; o resto dos navegadores
 * precisa. A checagem cobre os dois casos.
 */
export function Player({
  manifesto,
  sessaoId,
  posicaoInicial = 0,
}: {
  manifesto: string;
  sessaoId: string;
  posicaoInicial?: number;
}) {
  const video = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    const elemento = video.current;
    if (!elemento) return;

    let hls: Hls | null = null;

    if (elemento.canPlayType('application/vnd.apple.mpegurl')) {
      elemento.src = manifesto;
    } else if (Hls.isSupported()) {
      hls = new Hls({ enableWorker: true, lowLatencyMode: false });
      hls.loadSource(manifesto);
      hls.attachMedia(elemento);
    }

    if (posicaoInicial > 0) {
      elemento.currentTime = posicaoInicial;
    }

    const relogio = window.setInterval(() => {
      void reproducao
        .sinalDeVida(sessaoId, Math.floor(elemento.currentTime))
        .catch(() => undefined);
    }, 30_000);

    const aoSair = () => {
      void reproducao.encerrar(sessaoId, Math.floor(elemento.currentTime)).catch(() => undefined);
    };
    window.addEventListener('pagehide', aoSair);

    return () => {
      window.clearInterval(relogio);
      window.removeEventListener('pagehide', aoSair);
      aoSair();
      hls?.destroy();
    };
  }, [manifesto, sessaoId, posicaoInicial]);

  return (
    <div className="palco">
      <video ref={video} controls playsInline preload="metadata" />
    </div>
  );
}
