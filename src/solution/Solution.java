package solution;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import problem.ProblemSpec;
import solution.boxes.Movable;
import solution.boxes.MovingBox;
import solution.boxes.MovingObstacle;
import solution.states.Node;
import solution.states.State;
import solution.states.StateGraph;
import tester.Tester;

public class Solution {

    // The problem specification to solve
    private ProblemSpec spec;

    /**
     * Construct a new solution with based on a problem spec.
     *
     * @param spec The problem to solve.
     */
    public Solution(ProblemSpec spec) {
        this.spec = spec;
    }

    private List<State> getBoxStates(State startState, int index) {
        State goalState = startState.saveState();
        MovingBox box = goalState.mBoxes.get(index);
        box.setX(box.getXGoal());
        box.setY(box.getYGoal());
        List<Node<State>> nodes = new StateGraph(new Node<>(startState),
                new Node<>(goalState), StateGraph.GraphType.BOXES, index).aStar();

        if(nodes == null) {
            return null;
        }

        List<State> path = new ArrayList<>();
        for (int i = 0; i < nodes.size() - 1; i++) {
            path.addAll(State.interimBoxStates(nodes.get(i).getItem(), nodes.get(i + 1).getItem()));
        }
        path.addAll(State.interimBoxStates(path.get(path.size() - 1), goalState));

        return path;
    }

    private State handleMovingObs(State startState, int index) {
        State goalState = startState.saveState();
        goalState.current = -index - 1;
        List<Node<State>> nodes = null;
        while(nodes == null) {
            System.out.println("sample");
            MovingObstacle obs = goalState.mObstacles.get(index);
            int x = ThreadLocalRandom.current().nextInt(0, 1000)*10;
            int y = ThreadLocalRandom.current().nextInt(0, 1000)*10;
            obs.setX(x);
            obs.setY(y);
            if(goalState.isBoxCollision(obs)) {
                nodes = null;
                continue;
            }
            nodes = new StateGraph(new Node<>(startState), new Node<>(goalState),
                    StateGraph.GraphType.OBSTACLES, index).aStar();
        }

        return nodes.get(nodes.size() - 1).getItem();
    }

    /**
     * Rotate the robot from the initial state to a given rotation.
     * Generates all the states between to rotate the robot.
     *
     * @param startState The starting state.
     * @param rotation The rotation of the robot.
     * @return The states to rotate a robot.
     */
    private List<State> moveRobotInitial(State startState, BigDecimal rotation) {
        // make a goal state from the start state
        State goalState = startState.saveState();
        goalState.robot.setAngle(rotation);

        // generate the interim states
        List<State> movements = State.interimStates(startState, goalState);

        // actually rotate the robot
        startState.robot.setAngle(rotation);
        return movements;
    }

    private List<State> getAllBoxStates(State startState) {
        State goalState = startState.saveState();
        State prevState = startState.saveState();
        List<State> states = new ArrayList<>();
        states.add(startState);

        LinkedList<Integer> boxIndexes = new LinkedList<>();
        for(int i = 0; i < startState.mBoxes.size(); i++) {
            boxIndexes.add(i);

            goalState.mBoxes.get(i).setX(goalState.mBoxes.get(i).getXGoal());
            goalState.mBoxes.get(i).setY(goalState.mBoxes.get(i).getYGoal());
        }
        Collections.shuffle(boxIndexes);
        System.out.println(boxIndexes);

        int loop = 0;
        while(!boxIndexes.isEmpty()) {
            if(loop >= boxIndexes.size()) { //no possible box path -> try to move obstacles
                if(loop > boxIndexes.size()) { //previous attempt didn't work -> remove it and try again
                    states.remove(states.size() - 1);
                }
                if(prevState.mObstacles.size() == 0) { //there are actually no movable obstacles -> give up
                    return getAllBoxStates(startState);
                }
                int index = ThreadLocalRandom.current().nextInt(prevState.mObstacles.size());
                State tryState = handleMovingObs(prevState, index);//try something random
                if(tryState == null) {
                    return getAllBoxStates(startState);
                }
                states.add(tryState);
                prevState = states.get(states.size() - 1);
            }

            int i = boxIndexes.removeFirst();
            List<State> boxStates = getBoxStates(prevState, i);

            if(boxStates != null) {
                if(loop >= boxIndexes.size() + 1) { //omg we tried something and it worked!
                    int obsIndex = -prevState.current - 1;
                    states.remove(states.size() - 1);
                    List<Node<State>> nodes = new StateGraph(new Node<>(states.get(states.size() - 1)),
                            new Node<>(prevState), StateGraph.GraphType.OBSTACLES, obsIndex).aStar();
                    for(int j = 0; j < nodes.size() - 1; j++) {
                        states.addAll(State.interimBoxStates(nodes.get(j).getItem(), nodes.get(j + 1).getItem()));
                    }
                    states.addAll(State.interimBoxStates(states.get(states.size() - 1), prevState));
                }
                loop = 0;
                states.addAll(boxStates);
                prevState = states.get(states.size() - 1);
            } else { //did not work :'(
                loop += 1;
                boxIndexes.addLast(i);
            }
        }

        return states;
    }

    private List<State> robotStateToState(State startState, int index, Util.Side alignment) {
        int x = 0, y = 0;
        BigDecimal a = null;
        Movable box;
        if(index > 0){
            box = startState.mBoxes.get(index - 1);
        } else if(index < 0) {
            box = startState.mObstacles.get(-index - 1);
        } else {
            throw new RuntimeException("Invalid box index");
        }

        switch (alignment) {
            case BOTTOM:
                x = box.getX() + box.getWidth() / 2;
                y = box.getY();
                a = BigDecimal.ZERO;
                break;
            case TOP:
                x = box.getX() + box.getWidth() / 2;
                y = box.getY() + box.getHeight();
                a = BigDecimal.ZERO;
                break;
            case LEFT:
                x = box.getX();
                y = box.getY() + box.getHeight() / 2;
                a = BigDecimal.valueOf(90);
                break;
            case RIGHT:
                x = box.getX() + box.getWidth();
                y = box.getY() + box.getHeight() / 2;
                a = BigDecimal.valueOf(90);
                break;
        }

        State goalState = startState.saveState();
        goalState.robot.setX(x);
        goalState.robot.setY(y);
        goalState.robot.setAngle(a);

        List<Node<State>> nodes = new StateGraph(new Node<State>(startState),
                new Node<State>(goalState), StateGraph.GraphType.ROBOT, -1).aStar();
        List<State> path = new ArrayList<>();

        for (int i = 0; i < nodes.size() - 1; i++) {
            path.addAll(State.interimStates(nodes.get(i).getItem(), nodes.get(i + 1).getItem()));
            //path.add(nodes.get(i).getItem());
        }

        path.addAll(State.interimStates(path.get(path.size() - 1), goalState));

        return path;
    }

    private List<State> stateGenerationHelper(State startState, List<State> initPath, List<State> boxStates) {
        List<State> path = new ArrayList<>(initPath);
        Util.Side side = null;
        int index = 0;

        path.add(boxStates.get(0));
        for (int i = 1; i < boxStates.size(); i++) {
            State currentState = boxStates.get(i);
            State prevState = boxStates.get(i - 1);

            if (currentState.dir == null || currentState.current == 0) {
                continue;
            }
            if (currentState.dir != side || currentState.current != index) {
                side = currentState.dir;
                index = currentState.current;
                try {
                    path.addAll(robotStateToState(prevState, index, side));
                } catch(NullPointerException ex) {
                    System.out.println("Regenerating box path");
                    return stateGenerationHelper(startState, initPath, getAllBoxStates(startState));
                    //System.err.println("NPE caught - no robot path found");
                    //return boxStates;
                }
            }

            prevState = path.get(path.size() - 1);

            Movable box;
            if(currentState.current > 0) {
                box = currentState.mBoxes.get(currentState.current - 1);
            } else if(currentState.current < 0 ) {
                box = currentState.mObstacles.get(-currentState.current - 1);
            } else {
                throw new RuntimeException("Fatal exception");
            }

            switch (currentState.dir) {
                case RIGHT:
                    currentState.robot.setX(box.getX() + box.getWidth());
                    currentState.robot.setY(prevState.robot.getY());
                    currentState.robot.setAngle(prevState.robot.getAngle());
                    break;
                case LEFT:
                    currentState.robot.setX(box.getX());
                    currentState.robot.setY(prevState.robot.getY());
                    currentState.robot.setAngle(prevState.robot.getAngle());
                    break;
                case TOP:
                    currentState.robot.setY(box.getY() + box.getHeight());
                    currentState.robot.setX(prevState.robot.getX());
                    currentState.robot.setAngle(prevState.robot.getAngle());
                    break;
                case BOTTOM:
                    currentState.robot.setY(box.getY());
                    currentState.robot.setX(prevState.robot.getX());
                    currentState.robot.setAngle(prevState.robot.getAngle());
                    break;
            }
            path.add(currentState);
        }

        System.out.println("All generation complete");

        return path;
    }

    private List<State> generateStates(State startState) {
        System.out.println("Commencing generation");
        List<State> path = moveRobotInitial(startState, BigDecimal.ZERO);
        List<State> boxStates = getAllBoxStates(startState);
        System.out.println("Box generation complete");

        return stateGenerationHelper(startState, path, boxStates);
    }

    /**
     * Return the solution to the problem in terms of a list of atomic states.
     *
     * @return The list of states to solve a problem.
     */
    public List<State> solve() {
        return generateStates(new State(spec));
    }

    /**
     * Write a formatted solution to an output file.
     *
     * @param output The formatted solution.
     * @param outputFilename The filename to output the solution to.
     */
    public static void writeSolution(String output, String outputFilename) {
        try {
            BufferedWriter input = new BufferedWriter(
                    new FileWriter(outputFilename));
            input.write(output);
            input.flush();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("FileIO Error: could not output solution file");
            System.exit(4);
        }
    }

    /**
     * Load a problem with no solution.
     *
     * @param problemFile Filename of the problem file.
     * @return A problem spec with no solution.
     */
    public static ProblemSpec loadProblem(String problemFile) {
        ProblemSpec problem = new ProblemSpec();
        try {
            problem.loadProblem(problemFile);
        } catch (IOException e) {
            System.err.println("FileIO Error: could not load input file");
            System.exit(2);
        }
        return problem;
    }

    /**
     * Load a problem and solution into a problem spec.
     *
     * @param problemFile Filename of the problem file.
     * @param solutionFile Filename of the solution file.
     * @return A problem spec wih a solution.
     */
    public static ProblemSpec loadProblem(String problemFile, String solutionFile) {
        ProblemSpec problem = loadProblem(problemFile);
        try {
            problem.loadSolution(solutionFile);
        } catch (IOException e) {
            System.err.println("FileIO Error: could not read solution file");
            System.exit(3);
        }
        return problem;
    }

    /**
     * Use the tester on a specific problem to ensure correctness.
     *
     * @param problemSpec The problem specification with a solution.
     */
    private static void test(ProblemSpec problemSpec) {
        Tester tester = new Tester(problemSpec);

        if (problemSpec.getProblemLoaded() && problemSpec.getSolutionLoaded()) {
            System.out.println("Has initial state: " + tester.testInitialFirst());
            System.out.println("Correct step sizes: " + tester.testStepSize());
            System.out.println("Has no collisions: " + tester.testCollision());
            System.out.println("All pushes valid: " + tester.testPushedBox());
        } else if (problemSpec.getProblemLoaded()) {
            System.out.println("Problem has been loaded but no solution generated");
        } else {
            System.out.println("Problem has not been loaded correctly");
        }
    }

    /**
     * Load a problem and generate a solution based on the arguments provided.
     *
     * Usage: java ProgramName inputFileName outputFileName
     *
     * @param args Command line args.
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Invalid Usage: java ProgramName inputFileName outputFileName");
            System.exit(1);
        }

        String inputFile = args[0];
        String outputFile = args[1];

        ProblemSpec problemSpec = loadProblem(inputFile);
        Solution solution = new Solution(problemSpec);
        List<State> states = solution.solve();

        writeSolution(Formatter.format(states), outputFile);

        problemSpec = loadProblem(inputFile, outputFile);
        test(problemSpec);
    }
}
