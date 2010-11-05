package DLBanyan


object RulesUtil {

  abstract class Position 
  case class LeftP(n: Int) extends Position
  case class RightP(n: Int) extends Position
//  case object Outer extends Position


  // A proof rule returns None if it does not apply.
  // Otherwise it returns a list of subgoals
  // and a list of new free variables.
  abstract class ProofRule(name: String) extends 
    ((Position) =>   
        (Sequent) =>  
          Option[(List[Sequent], List[String])]) {
            override def toString: String = {
              name
            }
          }
  

  class LookupError() extends Exception()


  def replacelist[A](i: Int, lst: List[A], a: A): List[A] = lst match {
    case Nil => Nil
    case x::xs =>
      if(i <= 0) a::xs
      else x::replacelist(i-1,xs,a)      
  }

  def splicelist[A](i: Int, lst: List[A], a: List[A]): List[A] = lst match {
    case Nil => Nil
    case x::xs =>
      if(i <= 0) a ++ xs
      else x::splicelist(i-1,xs,a)      
  }

  //remove the ith element
  def removelist[A](i: Int, lst: List[A]): List[A] = lst match {
    case Nil => Nil
    case x::xs =>
      if(i <= 0) xs
      else x::removelist(i-1,xs)      
  }


  def replace(p: Position, s: Sequent,fm : Formula): Sequent = (p,s) match {
    case (LeftP(n), Sequent(ct,st)) => 
      if(n >= ct.length || n < 0 )
        throw new  LookupError()
      else Sequent(replacelist(n,ct,fm),st)
    case (RightP(n), Sequent(ct,st)) => 
      if(n >= st.length || n < 0 )
        throw new LookupError()
      else Sequent(ct,replacelist(n,st,fm))
  }

  def lookup(p: Position, s: Sequent): Formula = (p,s) match {
    case (LeftP(n), Sequent(ct,st)) => 
      if(n >= ct.length || n < 0 )
        throw new   LookupError()
      else ct.slice(n,n+1).head
    case (RightP(n), Sequent(ct,st)) => 
      if(n >= st.length || n < 0 )
        throw new LookupError()
      else st.slice(n,n+1).head
  }


  def remove(p: Position, s: Sequent): Sequent = (p,s) match {
    case (LeftP(n), Sequent(ct,st)) => 
      if(n >= ct.length || n < 0 )
        throw new  LookupError()
      else Sequent(removelist(n,ct),st)
    case (RightP(n), Sequent(ct,st)) => 
      if(n >= st.length || n < 0 )
        throw new LookupError()
      else Sequent(ct,removelist(n,st))
  }



/*
  def optionbind[A](op:Option[Option[A]]): Option[A] = op match {
    case Some(Some(x)) => Some(x)
    case Some(None) => None
    case None => None
  }

  def extractmap(p: Position, 
                 s: Sequent,
                 f: Formula => Option[Formula]): Option[Sequent] 
   = optionbind(extract(p,s).map(f1 => replacesequent(p,s,f(f1))))

*/

}

object Rules {

  import RulesUtil._

  val close = new ProofRule("close"){ 
    def apply(p: Position) = sq => {
      val fm = lookup(p,sq)
      (p,fm) match {
        case (LeftP(_), False) => 
          Some((Nil,Nil)) // proved!
        case (RightP(_), True) => 
          Some((Nil,Nil)) // proved!
        case (LeftP(n), fm) =>
          if(sq.scdts.contains(fm) )
            Some((Nil,Nil)) // proved!
          else None
        case (RightP(n), fm) =>
          if(sq.ctxt.contains(fm) )
            Some((Nil,Nil)) // proved!
          else None
      }
    } 
  }


  val hide = new ProofRule("hide") {
    def apply(p:Position) = sq => 
      Some( (List(remove(p,sq)   ), Nil )   )
  }

  val andLeft  = new ProofRule("andleft") {
    def apply(p:Position) = sq => (p,sq) match {
      case (LeftP(n), Sequent(c,s)) =>
        val fm = lookup(p,sq)
        fm match {
          case And(f1,f2) => 
            val sq1 = Sequent(splicelist(n,c,List(f1,f2)),s)
            Some( (List(sq1),Nil))
          case _ => 
            None
        }
      case _ => None
    }
  }

  val andRight  = new  ProofRule("andright") {
    def apply(p:Position) = sq => (p,sq) match { 
      case (RightP(n), Sequent(c,s)) =>
        val fm = lookup(p,sq)
        fm match {
          case And(f1,f2) => 
            val sq1 = replace(p,sq,f1)
            val sq2 = replace(p,sq,f2)
            Some( (List(sq1,sq2),Nil))
          case _ => 
            None
        }
      case _ => None
    }
  }

  val orRight = new ProofRule("orright") { 
    def apply(p:Position) = sq => (p,sq) match { 
      case (RightP(n), Sequent(c,s)) =>
        val fm = lookup(p,sq)
        fm match {
          case Or(f1,f2) => 
            val sq1 = Sequent(c,splicelist(n,s,List(f1,f2)))
            Some( (List(sq1),Nil))
          case _ => 
            None
        }
      case _ => None
    }
  }

  val orLeft = new  ProofRule("orleft") {
    def apply(p:Position) = sq => (p,sq) match { 
      case (LeftP(n), Sequent(c,s)) =>
        val fm = lookup(p,sq)
        fm match {
          case Or(f1,f2) => 
            val sq1 = replace(p,sq,f1)
            val sq2 = replace(p,sq,f2)
            Some( (List(sq1,sq2),Nil))
          case _ => 
            None
        }
      case _ => None
    }
  }


  val seq = new ProofRule("seq") {
    def apply(p: Position) = sq => {
      val fm  = lookup(p,sq)
      fm match {
        case  Box(Seq(h1,h2), phi) => 
           val fm1 = Box(h1,Box(h2,phi))
           val sq1 = replace(p,sq,fm1)
           Some( List(sq1),Nil)
        case _ => None
      }
    }
  }

  val chooseRight = new ProofRule("chooseright") {
    def apply(p: Position) = sq => (p,sq) match {
      case (RightP(n), Sequent(c,s)) =>
        val fm = lookup(p,sq)
        fm match {
          case Box(Choose(h1,h2), phi) => 
            val fm1 = Box(h1,phi) 
            val fm2 = Box(h2,phi)
            val sq1 = replace(p,sq,fm1)
            val sq2 = replace(p,sq,fm2)
            Some( (List(sq1,sq2),Nil))
          case _ => 
            None
        }
      case _ => None
    }
  }

  val checkRight = new ProofRule("checkright") {
    def apply(p: Position) = sq => (p,sq) match {
      case (RightP(n), Sequent(c,s)) =>
        val fm = lookup(p,sq)
        fm match {
          case Box(Check(fm1), phi) => 
            val Sequent(c1,s1) = replace(p,sq, phi)
            val sq1 = Sequent(fm1::c1,s1)
            Some( (List(sq1),Nil))
          case _ => 
            None
        }
      case _ => None
    }
  }


 val assignRight = new ProofRule("assignright") {
    def apply(p: Position) = sq => (p,sq) match {
     case (RightP(n),Sequent(c,s)) => 
      val fm = lookup(p,sq)
      fm match {
        case Box(Assign(vr,tm),phi) =>
          val vr1 = Prover.uniqify(vr)
          val phi1 = Prover.rename_Formula(vr,vr1,phi)
          val fm1 = Atom(R("=",List(Var(vr1),tm)))
          val Sequent(c1,s1) = replace(p,sq, phi1)
          Some((List(Sequent(fm1::c1,s1)),Nil))
        case _ =>
          None
      }
     case _ => None
   }
 }


  /* this assumes that we don't have any
   *  free variables from existentials */
 val assignAnyRight = new ProofRule("assignanyright") {
    def apply(p: Position) = sq => (p,sq) match {
     case (RightP(n),Sequent(c,s)) => 
      val fm = lookup(p,sq)
      fm match {
        case Box(AssignAny(vr),phi) =>
          val vr1 = Prover.uniqify(vr)
          val phi1 = Prover.rename_Formula(vr,vr1,phi)
          val sq1 = replace(p, sq, phi1)
          Some((List(sq1),Nil))
        case _ =>
          None
      }
     case _ => None
   }
 }


  val loopInduction : Formula => ProofRule = 
    inv => new ProofRule("loopInduction[" 
                         + Printing.stringOfFormula(inv) + "]") {
      def apply(pos: Position) = sq => (pos,sq) match {
        case (RightP(n), Sequent(c,s)) =>
          val fm = lookup(pos,sq)
          fm match {
            case Box(Loop(hp, True, inv_hints), phi) =>
              val initiallyvalid = 
                replace(pos, sq, inv)
              val inductionstep = 
                Sequent(List(inv), List(Box(hp, inv)))
              val closestep = 
                Sequent(List(inv), List(inv))
              Some((List(initiallyvalid, inductionstep, closestep),
                    Nil))
            case _ => 
              None
          }
        case _ => None
      }
    }


  val diffClose = new ProofRule("diffClose") {
    def apply(pos: Position) = sq => (pos,sq) match { 
      case(RightP(n), Sequent(c,s)) =>
        val fm = lookup(pos,sq)
        fm match {
          case Box(Evolve(derivs,h,_,_), phi) =>
            val closed = Sequent(List(h), List(phi))
            Some((List(closed), Nil))
          case _ => None
        }
      case _ => None
    }
  }  

  val diffStrengthen : Formula => ProofRule = 
    inv => new ProofRule("diffStrengthen["
                         + Printing.stringOfFormula(inv) + "]") {
      def apply(pos: Position) = sq => (pos,sq) match {
        case (RightP(n), Sequent(c,s)) =>
          println("checking diffstrengthen")
          val fm = lookup(pos,sq)
          fm match {
            case Box(Evolve(derivs,h,inv_hints,sols), phi) =>
              val (ind_asm, ind_cons) = 
                if(Prover.openSet(inv)) 
                  ( List(inv,h), 
                    Prover.setClosure(Prover.totalDeriv(derivs,inv)))
                else ( List(h), Prover.totalDeriv(derivs,inv))
              val inv_hints1 = inv_hints.filter( inv != _)
              val fm1 = Box(Evolve(derivs, And(h,inv),inv_hints1, sols), phi) 
              val iv = Sequent(h::c, List(inv))
              val ind = Sequent(ind_asm, List(ind_cons))
              val str = replace(pos,sq, fm1)
              Some((List(iv,ind,str), Nil))

            case _ => None
          }
        case _ => None
      }
    }

  sealed abstract class DiffSolveMode
  case object Standard extends DiffSolveMode
  case object Endpoint extends DiffSolveMode

  val diffSolve : DiffSolveMode => List[Formula] => ProofRule = 
    mode => fm_sols => new ProofRule("diffsolve[" + mode.toString() + "][" 
                          + fm_sols.map(Printing.stringOfFormula) 
                          + "]") {

      import Prover._

      class BadSolution extends Exception 

      def extract(sol: Formula): (String, (String, Term)) = sol match {
        case Forall(t, Atom(R("=", 
                              List(Fn(f, List(t1)),
                              sol_tm)))) if Var(t) == t1 =>
                                (t,(f,sol_tm))
        case _ => 
          println( sol)
        throw new BadSolution
      }

      def time_var(t_sols: List[(String,(String,Term))])
      : Option[String] = {
        val ts = t_sols.map(_._1)
        ts match {
          case Nil => None
          case (t ::rest ) =>
            if( rest.exists(x => x != t)){
              None
            } else {
              Some(t)
            }
        }
      }

      // TODO what if t is a variable in deriv?
      // XXX TODO check inital values
      def is_ok(t: String,
                deriv: (String,Term),
                sols: List[(String,Term)]  ) : Boolean  = deriv match {
        case (x, tm) =>
          println("testing if ok: " + x + "   " + tm)
          println("t= " + t)
          Prover.assoc(x,sols) match {
            case Some(sol) =>
              val dsol = totalDerivTerm(List((t,Num(Exact.one))), sol)
              val tm_sub = simul_substitute_Term(sols, tm)
     
              if(  polynomial_equality(tm_sub, dsol)     ) {
                println("it's ok")
                true
              } else {
                println("it's not ok")
                false
              }
            case None => 
              println("no corresponding solution found in:")
            println(sols)
            false
          }
       }

      def apply(pos: Position) = sq => (pos,sq, lookup(pos, sq)) match {
        case (RightP(n), 
              Sequent(c,s),
              Box(Evolve(derivs, h, _, _ ), phi)) =>
          val t_sols = fm_sols.map(extract)
          val sols = t_sols.map(_._2)
          time_var(t_sols) match {
            case None => None
            case Some(t) =>
              val oks = derivs.map(d => is_ok(t, d, sols))
            if(oks.contains(false))
              None
            else {
              val t2 = uniqify(t)
              val t_range = Atom(R(">=", List(Var(t), Num(Exact.zero))))
              val t2_range = 
                And(Atom(R(">=", List(Var(t2), Num(Exact.zero)))),
                    Atom(R("<=", List(Var(t2), Var(t)))))
              val endpoint_h = simul_substitute_Formula(sols, h)
              val interm_h = 
                rename_Formula(t,t2,simul_substitute_Formula(sols, h))
              val new_xs = sols.map(x => uniqify(x._1))
              val old_and_new_xs = 
                sols.map(_._1).zip(new_xs)
              val new_xs_and_sols = 
                new_xs.zip(sols.map(_._2))
              val assign_sols = 
                new_xs_and_sols.map(xtm => Assign(xtm._1,xtm._2))
              val phi1 = 
                old_and_new_xs.foldRight(phi)( (xs ,phi1) =>
                  rename_Formula(xs._1, xs._2, phi1))
              val assign_hp = 
                assign_sols.foldRight(Check(True):HP)((x,y) => Seq(x,y))
              val phi2 = 
                Box(assign_hp, phi1)
              val stay_in_h = 
                Forall(t2, Imp(t2_range, interm_h))
              val newgoal = mode match {
                case Standard =>
                  replace(pos,Sequent(stay_in_h ::t_range::c,s), phi2)
                case Endpoint =>
                  replace(pos,Sequent(endpoint_h ::t_range::c,s), phi2)
              }
              Some(List(newgoal), Nil)
            }
          }
          
        case _ => 
          None
      }
                            
                            
    }




  val substitute = new ProofRule("substitute") {
    import Prover._

    def apply(pos: Position) = sq => (pos,sq, lookup(pos, sq)) match {
      case (LeftP(n), Sequent(ctxt,sc), Atom(R("=", List(Var(v),tm)))) 
        if (ctxt ++ sc).forall(firstorder) =>
          val tm_vars = varsOfTerm(tm)
          val ctxt1 = removelist(n,ctxt)
          val ctxt2 = 
            ctxt1.map(x => substitute_Formula(v, tm, tm_vars, x))
          val sc1 = sc.map(x => substitute_Formula(v,tm,tm_vars, x))
          Some(List(  Sequent(ctxt2,sc1))    ,Nil)
      case _ =>
        None
    }

  }



  val directedCut : Formula => ProofRule = 
    fm => new ProofRule("directedCut["
                         + Printing.stringOfFormula(fm) + "]") 
  {
      def apply(pos: Position) = sq => (pos,sq) match {
        case (LeftP(n), Sequent(c,s)) =>
          val lem = Sequent(c, List(fm))
          val rep = replace(pos, sq, fm)
          Some(List(lem,rep     ), Nil)
        case  _ => None
        


      }
  }

}



/*
object PRArithmeticFV extends ProofRule{
  def applyRule(sq: Sequent): List[TreeNode] = sq match {
    case Sequent(ctxt, NoModality(fo) ) => 
      val fm = Imp(AM.list_conj(ctxt), fo);
      val c1 = new ArithmeticNode(fm)
      List(c1)
    case _ => 
      Nil
  }
}

object PRArithmetic extends ProofRule {
  def applyRule(sq: Sequent): List[TreeNode] = sq match {
    case Sequent(ctxt, NoModality(fo) ) => 
      val fm = Imp(AM.list_conj(ctxt), fo);
      val fm1 = AM.univ_close(fm);
      val c2 = new ArithmeticNode(fm1)
      List(c2)
    case _ => 
      Nil
  }
}

object PRRedlog extends ProofRule {
  def applyRule(sq: Sequent): List[TreeNode] = sq match {
    case Sequent(ctxt, NoModality(fo) ) => 
      val fm = Imp(AM.list_conj(ctxt.reverse), fo);
      val fm1 = AM.univ_close(fm);
      val c2 = new RedlogNode(fm1)
      List(c2)
    case _ => 
      Nil
  }
}


object PRMathematica extends ProofRule {
  def applyRule(sq: Sequent): List[TreeNode] = sq match {
    case Sequent(ctxt, NoModality(fo) ) => 
      val fm = Imp(AM.list_conj(ctxt.reverse), fo);
      val fm1 = AM.univ_close(fm);
      val c2 = new MathematicaNode(fm1)
      List(c2)
    case _ => 
      Nil
  }
}



object PRLoopClose extends ProofRule {
  def applyRule(sq: Sequent): List[TreeNode] = sq match {
    case Sequent(ctxt, Box(Repeat(p1, h, inv_hints), fm)) => 
      val pp = new AndNode("loop-close", 
                           sq,
                           BreadthFirst(),
                           List(Sequent(List(h), fm)))
      List(pp)
    case _ => Nil
  }
}


object PRLoopStrengthen extends ProofRule {
  
  def applyRule(sq: Sequent): List[TreeNode] = sq match {
    case Sequent(ctxt, Box(Repeat(p1, h, inv_hints), fm)) => 
      val loop_strengthen: Formula => AndNode = inv =>
        new AndNode(
                    "loop strengthening", 
                    sq,
                    DepthFirst(),	
                    List(Sequent(h::ctxt, NoModality(inv)),
                         Sequent(inv::h::Nil, 
                                 Box(p1, NoModality(Imp(h,inv)))),
                         Sequent(ctxt, 
                                 Box(Repeat(p1, 
                                            And(h,inv),
                                            inv_hints - inv),fm))))
      inv_hints.map(loop_strengthen)
    case _ => Nil
  }
}


object PRDiffSolve extends ProofRule {

  import Prover._

  class BadSolution extends Exception 

  def extract(sol: Formula): (String, (String, Term)) = sol match {
    case Forall(t, Atom(R("=", List(Fn(f, List(t1)),
                                    sol_tm)))) if Var(t) == t1 =>
       (t,(f,sol_tm))
    case _ => 
      println( sol)
      throw new BadSolution
  }

  def time_var(t_sols: List[(String,(String,Term))])
     : Option[String] = {
   val ts = t_sols.map(_._1)
   ts match {
      case Nil => None
      case (t ::rest ) =>
        if( rest.exists(x => x != t)){
          None
        } else {
          Some(t)
        }
    }
  }

// TODO what if t is a variable in deriv?
// TODO check inital values
  def is_ok(t: String,
            deriv: (String,Term),
            sols: List[(String,Term)]  ) : Boolean  = deriv match {
   case (x, tm) =>
     println("testing if ok: " + x + "   " + tm)
     println("t= " + t)
     Prover.assoc(x,sols) match {
       case Some(sol) =>
         println("sol= " + P.string_of_Term(sol))
         val dsol = totalDerivTerm(List((t,Num(ExactInt(1)))), sol)
         val tm_sub = simul_substitute_Term(sols, tm)
     
         if(  polynomial_equality(tm_sub, dsol)     ) {
           println("it's ok")
           true
         } else {
           println("it's not ok")
           false
         }
       case None => 
         println("no corresponding solution found in:")
         println(sols)
         false
     }
  }


  def applyRule(sq: Sequent): List[TreeNode] = sq match {
    case Sequent(ctxt, 
                 Box(Evolve(derivs, h, invs, fm_sols), phi)) =>
     val t_sols = fm_sols.map(extract)
     val sols = t_sols.map(_._2)
     time_var(t_sols) match {
       case None => Nil
       case Some(t) =>
         val oks = derivs.map(d => is_ok(t, d, sols))
         if(oks.contains(false))
           Nil
         else {
//           val t1 = uniqify(t)
           val t2 = uniqify(t)
//           val t1_range = Atom(R(">=", List(Var(t1), Num(ExactInt(0)))))
           val t_range = Atom(R(">=", List(Var(t), Num(ExactInt(0)))))
           val t2_range = 
             And(Atom(R(">=", List(Var(t2), Num(ExactInt(0))))),
                 Atom(R("<=", List(Var(t2), Var(t)))))
           val endpoint_h = simul_substitute_Formula(sols, h)
           val interm_h = 
             rename_Formula(t,t2,simul_substitute_Formula(sols, h))
           val new_xs = sols.map(x => uniqify(x._1))
           val old_and_new_xs = 
             sols.map(_._1).zip(new_xs)
           val new_xs_and_sols = 
             new_xs.zip(sols.map(_._2))
           val assign_sols = 
             new_xs_and_sols.map(xtm => Assign(xtm._1,xtm._2))
           val phi1 = 
             old_and_new_xs.foldRight(phi)( (xs ,phi1) =>
                                rename_DLFormula(xs._1, xs._2, phi1))
           val assign_hp = 
             assign_sols.foldRight(Check(True()):HP)((x,y) => Seq(x,y))
           val phi2 = 
             Box(assign_hp, phi1)
           val stay_in_h = 
             Forall(t2, Imp(t2_range, interm_h))
           List(
            new AndNode(
                    "solve differential equation", 
                    sq,
                    DepthFirst(),
                    List(Sequent(stay_in_h ::t_range::ctxt, 
                                 phi2))),
            new AndNode(
                    "solve differential equation, endpoint", 
                    sq,
                    DepthFirst(),
                    List(Sequent(endpoint_h ::t_range::ctxt, 
                                 phi2))))
         }
     }

    case _ => Nil 
  }
}


object PRSubstitute extends ProofRule {

  import Prover._
/*
  def isAssign(fm: Formula):Option[(String,Term)] = fm match {
    case Atom(R("=", List(Var(v), tm))) => Some(v,tm)
    case _ => None
  }
*/

  def findAssignment(ctxt: List[Formula])
       : Option[(String, Term, List[Formula])]  = ctxt match {
         case Nil => None
         case Atom(R("=", List(Var(v), tm))) :: rest =>
           Some((v,tm,rest))
         case fm::fms =>
           findAssignment(fms) match {
             case None => None
             case Some((v,tm,rest)) =>
               Some((v,tm,fm::rest))
           }
       }
                     
  def applyRule(sq: Sequent): List[TreeNode] = sq match {
    case Sequent(ctxt, NoModality(fo) ) => 
      val a = findAssignment(ctxt)
      a match {
        case None => Nil
        case Some((v,tm,ctxt1)) =>
          val tm_vars = varsOfTerm(tm)
          val ctxt2 = 
            ctxt1.map(x => substitute_Formula(v, tm, tm_vars, x))
          val fo2 = substitute_Formula(v,tm,tm_vars, fo)
          List(new OrNode(Sequent(ctxt2,NoModality(fo2))))
      }
    case _ => Nil
  }

}


object PRAlpha extends ProofRule {
  
  import Prover._

  def matcher(fm: Formula): Option[List[Formula]] = fm match {
    case And(fm1, fm2) => Some(List(fm1,fm2))
    case True() => Some(Nil)
    case _ => None
  }


  def applyRule(sq: Sequent): List[TreeNode] = sq match {
    case Sequent(ctxt, NoModality(Imp(fm1,fm2))) =>
      val ctxt2 = fm1 :: ctxt
      List(new OrNode(Sequent(ctxt2, NoModality(fm2))))
    case Sequent(ctxt, phi) =>
      matchAndSplice(ctxt, matcher) match {
        case None => Nil
        case Some(ctxt1) =>
          List(new OrNode(Sequent(ctxt1,phi)))
      }
  }
}


object PRBeta extends ProofRule {
  

  def applyRule(sq: Sequent): List[TreeNode] = sq match {
    case Sequent(ctxt, NoModality(And(fm1,fm2))) =>
      List(new AndNode(
                  "Beta ",
                  sq,
                  BreadthFirst(),
              List(Sequent(ctxt, NoModality(fm1)),
                   Sequent(ctxt, NoModality(fm2)))))
    case _ => Nil
  }
}



// untested
object PRAllLeft extends ProofRule {
  
  import Prover._

  def matcher(v1: String)(fm: Formula): Option[List[Formula]] = fm match {
    case Forall(v, fm) => 
      Some(List(simul_substitute_Formula(List((v,Var(v1))), fm)))
    case _ => None
  }

  def applyRule(sq: Sequent): List[TreeNode] = sq match {
    case Sequent(ctxt, NoModality(fm)) =>
      val fvs = (AM.fv(fm) :: ctxt.map(AM.fv)).flatten[String].removeDuplicates
      fvs.map(x => matchAndSplice(ctxt, matcher(x)) match {
                    case None => Nil
                    case Some(ctxt1) =>
                      List(new OrNode(Sequent(ctxt1, NoModality(fm))))
                  }).flatten[TreeNode]
  
  }


}


*/
