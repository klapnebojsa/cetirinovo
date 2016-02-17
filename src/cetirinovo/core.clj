(ns cetirinovo.core  
  (:require [midje.sweet :refer :all]
            [clojure.java.io :as io]
            [clojure.core.async :refer [chan <!!]]
            [uncomplicate.clojurecl
             [core :refer :all]
             [info :refer [info endian-little]]]
            [vertigo
             [bytes :refer [direct-buffer byte-seq]]
             [structs :refer [wrap-byte-seq int8]]]))

#_(alter-var-root
   (var uncomplicate.clojurecl.core/*opencl-2*)
   (fn [f]  false))
(def data-file (io/file
                 (io/resource 
                   "examples/hello-kernel.cl" )))
(let [notifications (chan)
      follow (register notifications)work-sizes (work-size [1])
      host-msg (direct-buffer 16)
      program-source 
      "__kernel void hello_kernel(__global char16 *msg) {\n    *msg = (char16)('H', 'e', 'l', 'l', 'o', ' ',
   'k', 'e', 'r', 'n', 'e', 'l', '!', '!', '!', '\\0');\n}\n"
      ]
  (try
    (with-release [devs (devices (first (platforms))) 
                   dev (first devs)
                   ctx (context [dev])
                   cqueue (command-queue ctx dev)
                   cl-msg (cl-buffer ctx 16 :write-only)
                   prog (build-program! (program-with-source ctx [program-source]))
                   hello-kernel (kernel prog "hello_kernel")
                   read-complete (event)
                 ]
      (println "dev1: " dev)
      (println "ctx1: " ctx)
      (println "cqueue1: " cqueue) 
      (println "prog1: " prog)
      (println "hello-kernel1: " hello-kernel)
      (println "read-complete1: " read-complete)

      (set-args! hello-kernel cl-msg)
      (enq-nd! cqueue  hello-kernel work-sizes)
      (enq-read! cqueue cl-msg host-msg)
      (apply str (map char
                      (wrap-byte-seq int8 (byte-seq host-msg)))))
    (catch Exception e (println "Greska 11111111: " (.getMessage e)))
  )
  )

(println "drugi deo")
;---------------------------------------------------------------------------------------
(let [notifications (chan)
      follow (register notifications)]
  (try
    (with-release [dev (first (devices (first (platforms))))
                    ctx (context [dev])
                    cqueue (command-queue ctx dev)]
      (println "dev: " dev)
      (println "ctx: " ctx)
      (println "cqueue: " cqueue)
      (facts
        "Section 4.1, Page 69."
        (let [host-msg (direct-buffer 16)
              work-sizes (work-size [1])        
              program-source (slurp (io/reader "examples/hello-kernel.cl" ))]
          (println "program-source 2222: " program-source)       
          (with-release [cl-msg (cl-buffer ctx 16 :write-only)
                         prog (build-program! (program-with-source ctx [program-source]))
                         hello-kernel (kernel prog "hello_kernel")
                         read-complete (event)]
            (println "cl-msg: " cl-msg)
            (println "prog: " prog)
            (println "hello-kernel: " hello-kernel)         
            (println "read-complete: " read-complete) 
          
            (set-args! hello-kernel cl-msg) => hello-kernel
            (enq-nd! cqueue hello-kernel work-sizes) => cqueue
            (enq-read! cqueue cl-msg host-msg read-complete) => cqueue
            (follow read-complete host-msg) => notifications
            (apply str (map char
                            (wrap-byte-seq int8 (byte-seq (:data (<!! notifications))))))
            => "Hello kernel!!!\0"))))
    (catch Exception e (println "Greska 222222222: " (.getMessage e)))
  ))
      
(println "treci deo") 
;---------------------------------------------------------------------------------------
(let [notifications (chan)
      follow (register notifications)]
  (try
    (with-release [dev (first (devices (first (platforms))))
                    ctx (context [dev])
                    cqueue (command-queue ctx dev)]
      (println "dev: " dev)
      (println "ctx: " ctx)
      (println "cqueue: " cqueue)
      (facts
        "Section 4.2, Page 72."
        (let [host-a (float-array [10])
              host-b (float-array [2])
              host-out (float-array 1)
              work-sizes (work-size [1])
              program-source
             (slurp (io/resource "examples/double-test.cl"))]
          (println "program-source 33333: " program-source)
          (with-release [cl-a (cl-buffer ctx (* 2 Float/BYTES) :read-only)
                         cl-b (cl-buffer ctx (* 2 Float/BYTES) :read-only)
                         cl-out (cl-buffer ctx (* 2 Float/BYTES) :write-only)
                         prog (build-program! (program-with-source ctx [program-source])
                                              (if (contains? (info dev :extensions)
                                                             "cl_khr_fp64")
                                                "-DFP_64"
                                                "")
                                              notifications)
                         double-test (kernel prog "double_test")]
            (set-args! double-test cl-a cl-b cl-out) => double-test
            (enq-write! cqueue cl-a host-a) => cqueue
            (enq-write! cqueue cl-b host-b) => cqueue
            (enq-nd! cqueue double-test work-sizes) => cqueue
            (enq-read! cqueue cl-out host-out) => cqueue
            (seq host-out) => (map / host-a host-b)))))
    (catch Exception e (println "Greska 333333333: " (.getMessage e)))
  ))






(println "cetvrti deo") 
;---------------------------------------------------------------------------------------
(let [notifications (chan)
      follow (register notifications)]
  (try
    (with-release [dev (first (devices (first (platforms))))
                    ctx (context [dev])
                    cqueue (command-queue ctx dev)]  
      (facts
        "Section 4.3, Page 77."
        (println "Single FP Config: " (info dev :single-fp-config)))
      (facts
       "Section 4.4.1, Page 79."
       (println "Preferred vector widths: "
                (select-keys (info dev) [:preferred-vector-width-char
                                         :preferred-vector-width-short
                                         :preferred-vector-width-int
                                         :preferred-vector-width-long
                                         :preferred-vector-width-float
                                         :preferred-vector-width-double
                                         :preferred-vector-width-long])))
      (facts
        "Section 4.4.4, Page 85."
        (let [host-data (byte-array 16)
              work-sizes (work-size [1])
              program-source
              (slurp (io/reader "examples/vector-bytes.cl"))]
          (println "program-source 4444: " program-source)
          (with-release [cl-data (cl-buffer ctx 16 :write-only)
                         prog (build-program! (program-with-source ctx [program-source]))
                         vector-bytes (kernel prog "vector_bytes")]
            (set-args! vector-bytes cl-data) => vector-bytes
            (enq-write! cqueue cl-data host-data) => cqueue
            (enq-nd! cqueue vector-bytes work-sizes) => cqueue
            (enq-read! cqueue cl-data host-data) => cqueue
            (seq host-data) => (if (endian-little dev)
                                 [3 2 1 0 7 6 5 4 11 10 9 8 15 14 13 12]
                                 (range 16))))))
    (catch Exception e (println "Greska 444444: " (.getMessage e)))
  ))