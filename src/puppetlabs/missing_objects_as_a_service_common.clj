(ns puppetlabs.missing-objects-as-a-service-common
  (:require [schema.core :as schema]
            [clojure.tools.logging :as log])
  (:import (clojure.lang IFn Agent Atom IDeref)))

  (schema/defn ^:always-validate create-agent :- Agent
               "Creates and returns the agent used by the file sync client service.
               'request-shutdown' is a function that will be called in the event of a fatal
               error during the sync process when the entire applications needs to be shut down.
               Additionally, scheduled-jobs-completed should be a promise which will get
               '(deliver scheduled-jobs-completed true)' on a fatal error.  This indicates
               to the service that no more jobs will be scheduled - this is known to be true
               when the Agent's error handler runs, since subsequent jobs are only scheduled
               upon the successful completion of the previous job.  scheduled-jobs-completed
               should be the promise that is deref'd during the service's stop function,
               otherwise that deref will timeout, thereby preventing shutdown of the process
               for the duration of its timeout (20 minutes)."
               [request-shutdown :- IFn
                scheduled-jobs-completed :- IDeref
                agent-name]
               (agent
                 {:status :created}
                 :error-mode :fail
                 :error-handler (fn [_ error]
                                  ; disaster!  shut down the entire application.
                                  (log/error error (format "Fatal error during %s, requesting shutdown." agent-name))
                                  (request-shutdown)
                                  (deliver scheduled-jobs-completed true))))
