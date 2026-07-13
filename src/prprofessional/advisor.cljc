(ns prprofessional.advisor
  "PublicRelationsProfessionalsAdvisor — proposes a press-release
  operation (approve a release, publish a release) for a registered
  organization. Swappable mock/llm; the advisor ONLY proposes —
  `prprofessional.governor` checks the embargo floor and spokesperson
  membership independently. Modeled on cloud-itonami-isco-4311's
  advisor.

  A proposal: {:op :approve-release|:publish-release
               :effect :propose :release-id str :as-of-day int
               :quoted-spokespersons #{str} :stake kw :confidence n
               :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake release-id as-of-day quoted-spokespersons] :as request}]
  {:op op
   :effect :propose
   :release-id release-id
   :as-of-day as-of-day
   :quoted-spokespersons quoted-spokespersons
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a public relations advisor. Given a request, propose an
   :op, the :release-id, :as-of-day and :quoted-spokespersons, an
   honest :confidence and a :stake. Never call an embargo-violating or
   unauthorized-attribution release conforming — the governor checks
   both against the registered release record.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
