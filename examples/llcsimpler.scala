dl('load, "examples/llcsimpler.dl")
val rl = loopInduction(
  parseFormula(
    "(A() > 0 & B() > 0 & ~(f() = l()) & eps() > 0) &" + 
    "(((B()*x(l()) > B()*x(f()) + " + 
    "(1/2) * (v(f())^2 -  v(l())^2) & " +
    "x(l()) > x(f()) &" +
    "v(f()) >= 0 &" +
    "v(l()) >= 0 )    )   )"))


val cuttct = cutT(
  DirectedCut,
  parseFormula(
    "B()*X1>B()*X2+1/2*(V1^2-V2^2)+" + 
     "(A()+B())*(1/2*A()*eps()^2+eps()*V1)"
  ),
  parseFormula(
    "B()*X1>B()*X2+1/2*(V1^2-V2^2)+" + 
     "(A()+B())*(1/2*A()*s()^2+s()*V1)"
  )
)

  
val everythingT: Tactic = 
  composeT(
    repeatT(
      eitherlistT(List(hpalphaT, 
                       alphaT, 
                       nonarithcloseT,
                       betaT, 
                       substT))),
    eitherT(nonarithcloseT, hidethencloseT))





val ch_brake = 
  composelistT(List(repeatT(hpalpha1T),
                    diffsolveT(RightP(1),Endpoint),
                    repeatT(hpalpha1T),
                    instantiate0T(St("C")),
                    repeatT(substT),
                    hideunivsT(St("C")),
                    repeatT(nullarizeT),
                    repeatT(vacuousT),
                    everythingT
                      ))

val whatev_finish = composelistT(List(
        repeatT(nullarizeT),
        repeatT(substT),
        repeatT(tryruleT(andRight))
    ))


val ch_whatev = 
  composelistT(List(repeatT(hpalpha1T),
                    diffsolveT(RightP(1),Standard),
                    tryruleT(update),
                    tryruleatT(prenexify)(LeftP(0)),
                    tryruleatT(commutequantifiers)(LeftP(0)),
                    repeatT(hpalpha1T),
                    instantiate0T(St("C")),
                    repeatT(substT),
                    hideunivsT(St("C")),
                    repeatT(hpalpha1T),
                    repeatT(vacuousT),
                    branchT(tryruleT(impLeft),
                            List(branchT(tryruleT(impLeft),
                                         List(whatev_finish,
                                              composelistT(
                                                List(tryruleT(not),
                                                     alleasyT)))
                                       ),
                                 composelistT(
                                   List(tryruleT(not),
                                        tryruleT(close)))))
                  ))



val indtct =                           
  composeT(
   repeatT(eitherT(hpalphaT,alphaT)),
   branchT(tryruleT(choose),
           List(ch_brake,ch_whatev)))

    



dl('gotoroot)
dl('tactic,  branchT(tryruleT(rl),
                     List(tryruleatT(close)(RightP(0)),
                          indtct,
                          repeatT(trylistofrulesT(List(close,andLeft)))
                          )))


/*
dl('tactic, trylistofrulesT(List(rl)))
dl('tactic, applyToLeavesT(tryruleatT(close) (RightP(0))))
dl('tactic, applyToLeavesT(repeatT(alphaT)))
dl('tactic, applyToLeavesT(tryruleatT(close) (RightP(0))))
dl('tactic, applyToLeavesT(repeatT(eitherT(hpalphaT,alphaT))))
dl('tactic, applyToLeavesT(trylistofrulesT(List(
  qDiffSolve(Endpoint)(List(
    parseFormula("forall s . x(s, i) = (1/2) *a(i) * s^2 + v(i) * s + x(i)"),
    parseFormula("forall s . v(s, i) = a(i) * s + v(i)"),
    parseFormula("forall s . t(s) = t()  + s")
    ))))))
*/
