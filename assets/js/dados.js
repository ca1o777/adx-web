/* ==========================================================
   ADX – Dados de demonstração do frontend
   ----------------------------------------------------------
   Nesta etapa (1º bimestre) a aplicação não possui backend.
   Os dados abaixo simulam o que será retornado pelo servidor
   e pelo banco de dados, que serão entregues no 2º bimestre.
   ========================================================== */

window.ADX = {
  usuario: {
    id: 3,
    nomeExibicao: "Caio Fernandes",
    nomeCompleto: "Caio Fernandes Soares",
    email: "caio.soares@exemplo.com",
    modoPreferido: "Blitz",
    sobre: ""
  },

  /* Partidas do usuário logado. O número (id) segue a ordem de registro. */
  partidas: [
    { id: 1,  data: "2026-07-25T19:40", adversario: "Bruno Tavares",   modo: "Rápido",   resultado: "Vitória" },
    { id: 2,  data: "2026-07-29T20:05", adversario: "Juliana Prates",  modo: "Rápido",   resultado: "Derrota" },
    { id: 3,  data: "2026-08-02T15:30", adversario: "Paula Ribeiro",   modo: "Clássico", resultado: "Empate"  },
    { id: 4,  data: "2026-08-06T21:10", adversario: "Rafael Moura",    modo: "Rápido",   resultado: "Vitória" },
    { id: 5,  data: "2026-08-10T22:00", adversario: "Diego Sampaio",   modo: "Blitz",    resultado: "Vitória" },
    { id: 6,  data: "2026-08-13T19:15", adversario: "Letícia Andrade", modo: "Rápido",   resultado: "Derrota" },
    { id: 7,  data: "2026-08-16T16:45", adversario: "Marcos Vilela",   modo: "Clássico", resultado: "Vitória" },
    { id: 8,  data: "2026-08-19T21:30", adversario: "Bruno Tavares",   modo: "Blitz",    resultado: "Vitória" },
    { id: 9,  data: "2026-08-22T20:20", adversario: "Juliana Prates",  modo: "Blitz",    resultado: "Derrota" },
    { id: 10, data: "2026-08-25T19:50", adversario: "Paula Ribeiro",   modo: "Rápido",   resultado: "Derrota" },
    { id: 11, data: "2026-08-28T17:00", adversario: "Rafael Moura",    modo: "Clássico", resultado: "Empate"  },
    { id: 12, data: "2026-08-31T20:40", adversario: "Diego Sampaio",   modo: "Rápido",   resultado: "Vitória" },
    { id: 13, data: "2026-09-03T21:55", adversario: "Letícia Andrade", modo: "Blitz",    resultado: "Vitória" },
    { id: 14, data: "2026-09-06T19:25", adversario: "Marcos Vilela",   modo: "Rápido",   resultado: "Derrota" },
    { id: 15, data: "2026-09-08T16:10", adversario: "Bruno Tavares",   modo: "Clássico", resultado: "Vitória" },
    { id: 16, data: "2026-09-11T20:35", adversario: "Juliana Prates",  modo: "Rápido",   resultado: "Empate"  },
    { id: 17, data: "2026-09-13T22:15", adversario: "Paula Ribeiro",   modo: "Blitz",    resultado: "Vitória" },
    { id: 18, data: "2026-09-15T19:05", adversario: "Rafael Moura",    modo: "Rápido",   resultado: "Derrota" },
    { id: 19, data: "2026-09-18T15:40", adversario: "Diego Sampaio",   modo: "Clássico", resultado: "Vitória" },
    { id: 20, data: "2026-09-20T21:20", adversario: "Marcos Vilela",   modo: "Blitz",    resultado: "Vitória" },
    { id: 21, data: "2026-09-22T20:00", adversario: "Letícia Andrade", modo: "Rápido",   resultado: "Empate"  },
    { id: 22, data: "2026-09-24T19:35", adversario: "Bruno Tavares",   modo: "Rápido",   resultado: "Vitória" },
    { id: 23, data: "2026-09-26T16:50", adversario: "Juliana Prates",  modo: "Clássico", resultado: "Derrota" },
    { id: 24, data: "2026-09-27T21:14", adversario: "Rafael Moura",    modo: "Blitz",    resultado: "Vitória" }
  ],

  /* Demais jogadores cadastrados (totais já consolidados). */
  jogadores: [
    { id: 1, nome: "Juliana Prates",  vitorias: 19, empates: 5, derrotas: 6  },
    { id: 2, nome: "Rafael Moura",    vitorias: 16, empates: 4, derrotas: 7  },
    { id: 4, nome: "Bruno Tavares",   vitorias: 11, empates: 3, derrotas: 8  },
    { id: 5, nome: "Letícia Andrade", vitorias: 9,  empates: 6, derrotas: 4  },
    { id: 6, nome: "Marcos Vilela",   vitorias: 8,  empates: 2, derrotas: 10 },
    { id: 7, nome: "Paula Ribeiro",   vitorias: 6,  empates: 3, derrotas: 6  },
    { id: 8, nome: "Diego Sampaio",   vitorias: 5,  empates: 4, derrotas: 8  }
  ]
};
