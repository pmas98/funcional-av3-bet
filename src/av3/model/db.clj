(ns av3.model.db
  (:require [clj-http.client :as client]))

;; Estado em memória
(def contas (atom {:saldo 0}))
(def apostas (atom {}))

;; Operações de conta
(defn obter-saldo []
  {:saldo (:saldo @contas)})

(defn depositar [valor]
  (swap! contas update :saldo + valor)
  {:saldo (:saldo @contas)})

;; Função pura para registrar uma aposta
(defn registrar-aposta [id-aposta bookmaker market multiplier outcome valor sport]  ;; Adicionando sport
  (let [current-saldo (:saldo @contas)]
    (cond
      (<= valor 0)
      {:status "erro"
       :message "O valor da aposta deve ser maior que zero."}

      (> valor current-saldo)
      {:status "erro"
       :message "O valor da aposta não pode ser maior que o saldo disponível."}

      :else
      (let [new-aposta {:bookmaker bookmaker
                        :market market
                        :outcome outcome
                        :multiplier multiplier
                        :valor valor
                        :sport sport  ;; Incluindo sport no novo mapa de aposta
                        :status "pendente"}]
        (swap! apostas assoc id-aposta new-aposta)
        (swap! contas update :saldo - valor)
        {:aposta-id id-aposta
         :status "registrada"}))))


;; Função para consultar uma aposta
(defn consultar-aposta [id-aposta]
  (get @apostas id-aposta))

(defn obter-resultado [id-aposta sport]
  (let [url (str "https://api.the-odds-api.com/v4/sports/" sport "/scores/?apiKey=083e159eb384001b00ba52c8fd8f4513&eventIds=" id-aposta)
        response (client/get url {:as :json})]
    (if (= 200 (:status response))
      (first (:body response))  ;; Retorna o primeiro corpo da resposta se a chamada for bem-sucedida
      {:status (:status response) :error "Erro ao obter resultados"})))  ;; Retorna um erro se a chamada falhar

(defn liquidar-aposta [id-aposta]
  (let [aposta (get @apostas id-aposta)]
    (if (nil? aposta)
      {:status "erro"
       :message "Aposta não encontrada."}
      (let [sport (:sport aposta)  ;; Obtenha o esporte da aposta
            resultado (obter-resultado id-aposta sport)]  ;; Passe o esporte para obter-resultado
        (if (and (= (:completed resultado) true)
                 (= (:home_team resultado) (:outcome aposta)))  ;; Verifica se o time vencedor é o resultado esperado
          (do
            (swap! apostas update id-aposta assoc :status "liquidada")
            (let [{:keys [valor multiplier]} aposta
                  retorno (* valor multiplier)]
              (swap! contas update :saldo + retorno)
              {:status "sucesso"
               :message "Aposta liquidada com sucesso."
               :retorno retorno}))
          (if (and (= (:completed resultado) true)
                   (not= (:home_team resultado) (:outcome aposta)))  ;; Se o jogo foi completado e o time não ganhou
            (do
              (swap! apostas update id-aposta assoc :status "liquidada")
              {:status "sucesso"
               :message "Aposta liquidada como perdedora."})
            {:status "erro"
             :message "Resultado inválido ou aposta não completada."}))))))

