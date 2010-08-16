package DLBanyan

import scala.actors.Actor
import scala.actors.Actor._


import Nodes._


object TreeActions {
  

  import RulesUtil._
  import Procedures._


  val jobs = new scala.collection.mutable.HashMap[NodeID, Long]()
  val jobmaster = new Jobs.JobMaster()
  jobmaster.start

  var hereNode: ProofNode = nullNode


  var treemodel: Option[DLBanyan.GUI.TreeModel] = None

  def getnodethen(ndID: NodeID, f: (ProofNode) => Unit ): Unit = 
    nodeTable.get(ndID) match {
      case Some(nd) => f(nd)
      case None =>
        println ("node " + ndID + " does not exist.")
    }

  def gotonode(nd: ProofNode) : Unit = {
        hereNode = nd
        println("now at node " + nd.nodeID )
    }

  def shownode(nd: ProofNode) : Unit = 
        println(nd.toPrettyString)

/*
  def showhints(nd: ProofNode, pos: Position): Unit = {
    val fm = lookup(pos, nd.goal)
    fm match {
      case ...
    }
  }
  
*/

  def attachnode(pt: ProofNode, newnd: ProofNode): Unit = {
    newnd.parent = Some(pt.nodeID)
    register(newnd)
    pt.children = newnd.nodeID :: pt.children
    treemodel.map(_.fireNodesInserted(pt)) // GUI
  }

  def applyrule(hn: OrNode, 
                p: Position, 
                rl: ProofRule): Option[List[NodeID]] = rl(p)(hn.goal) match {
    case Some((Nil, _)) => //proved
      val pnd = new DoneNode(rl.toString, hn.goal)
      attachnode(hn,pnd)
      propagateProvedUp(hn.nodeID, pnd.nodeID)
      Some(Nil)
    case Some((List(sq), _)) => 
      val ornd = new OrNode(rl.toString, sq)
      attachnode(hn,ornd)
      Some(List(ornd.nodeID))
    case Some( (sqs, fvs)  ) =>
      val andnd = new AndNode(rl.toString, hn.goal, Nil)
      attachnode(hn,andnd)
      val subname = rl.toString + " subgoal"
      val ornds = sqs.map(s => new OrNode(subname, s))
      ornds.map(x => attachnode(andnd, x))
      Some(ornds.map(_.nodeID))
    case None => 
      None
  }

  def submitproc(ornd: OrNode, proc: String): Boolean
  = procs.get(proc) match {
    case None => false
    case Some(pr) => 
      if(pr.applies(ornd.goal)) {
        jobmaster ! (('job, proc, ornd.goal, ornd.nodeID))
        val t = System.currentTimeMillis
        jobs.put(ornd.nodeID, t)
        true
      } else {
        false
      }
  }
    

/* crawl the tree to update statuses.
 * propagateProvedUp is called on nd when a child of nd is proved.
 */
  def propagateProvedUp(ndID: NodeID, from: NodeID): Unit = {
    val nd = getnode(ndID)
    nd match {
      case AndNode(r,g,svs) =>
        val others = nd.children.filterNot( _ ==  from)
        val os = others.map(getnode).map(_.status)
        os.find( _ != Proved) match {
           case None =>
             nd.status = Proved
             treemodel.map(_.fireChanged(nd)) // GUI
             nd.parent match {
               case Some(p) =>
                 propagateProvedUp(p, ndID)
               case None =>
             }    
           case Some(_) =>
        }

      case OrNode(r,g) =>
        nd.status = Proved
        treemodel.map(_.fireChanged(nd)) // GUI
        val others = nd.children.filterNot( _ ==  from)
        others.map(x => propagateIrrelevantDown(x))
        nd.parent match {
          case Some(p) =>
            propagateProvedUp(p, ndID)
          case None =>
            
        }
      case DoneNode(r,g) =>
        // shouldn't ever happen
        throw new Error("bad call of propagateProvedUp")
    }
  }

  // called when ndID becomes irrelevant.
  def propagateIrrelevantDown(ndID: NodeID) : Unit = {
    val nd = getnode(ndID)
    nd.status = Irrelevant(nd.status)
    treemodel.map(_.fireChanged(nd)) //GUI
    // TODO check if we have any pending jobs. cancel them.
    nd.children.map( propagateIrrelevantDown)

  }


}



class FrontActor extends Actor {

  import TreeActions._  
  import RulesUtil._

  import Tactics.Tactic




  def act(): Unit = {
    println("acting")

    while(true){
      receive {
        case 'quit =>
          println("frontactor quitting")
          jobmaster !? 'quit
          sender ! ()
          exit
        case 'gui => 
          val fe = DLBanyan.GUI.FE.start(self)
          sender ! ()
        case ('registergui, tm: DLBanyan.GUI.TreeModel) => 
          treemodel = Some(tm)
        case 'here =>
          displayThisNode
          sender ! ()
        case ('load, filename:String) =>
          loadfile(filename)
          sender ! ()
        case ('show, nd: NodeID) =>
          getnodethen(nd, shownode _)
          sender ! ()
        case ('goto, nd: NodeID) =>
          getnodethen(nd, gotonode _)
          getnodethen(nd, shownode _)
          sender ! ()
        case ('rule, pos: Position, rl: ProofRule) =>
          hereNode  match {
            case ornd@OrNode(_,_) =>
              val r = applyrule(ornd,pos,rl)
              r match {
                case Some(_) => 
                   println("success")
                case None => 
                  println("rule cannot be applied there")    
              }

            case _ => 
              println("cannot apply rule here")
          }
          sender ! ()

        case ('tactic, tct: Tactic) =>
          hereNode  match {
            case ornd@OrNode(_,_) =>
              tct(ornd)
            case _ => 
              println("cannot apply rule here")
          }
          sender ! ()


        case ('job, proc: String) => 
          hereNode match {
            case ornd@OrNode(r,sq) =>
              val res = submitproc(ornd, proc)
              if(res) ()
              else println("procedure " + proc + " does not apply here.")
            case _ =>
              println("can only do a procedure on a ornode")
              
          }
          sender ! ()

        case ('jobdone, ndID: NodeID, sq: Sequent) =>
          jobs.remove(ndID)
          nodeTable.get(ndID) match {
            case Some(nd) => 
              val nd1 = DoneNode("quantifier elimination", sq)
              attachnode(nd, nd1)
              propagateProvedUp(ndID, nd1.nodeID)
            case None => 
              throw new Error("node not in nodeTable")
          }


        case ('jobdone, ndID: NodeID) =>
          jobs.remove(ndID)


        case 'jobs =>
          println(jobs.toList)
          sender ! ()

        case msg =>
          println("got message: " + msg)
          sender ! ()
      }
    }
    
  }


  def displayThisNode : Unit = {
    shownode(hereNode)
  }




  def loadfile(filename: String) : Unit = try {
    val fi = 
      new java.io.FileInputStream(filename)

    val dlp = new DLParser(fi)

    dlp.result match {
      case Some(g) =>
        val nd = new OrNode("loaded from " + filename, g)
        register(nd)
        hereNode = nd
        rootNode = nd 
        treemodel.map(_.fireNewRoot(nd))// GUI
      case None =>
        println("failed to parse file")

    }

    ()

  } catch {
    case e => println("failed to load file")
  }
}



