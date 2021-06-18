package uk.ac.bris.cs.scotlandyard.model;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;
import static uk.ac.bris.cs.scotlandyard.model.Colour.BLACK;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.DOUBLE;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.SECRET;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import uk.ac.bris.cs.gamekit.*;
import uk.ac.bris.cs.gamekit.graph.Edge;
import uk.ac.bris.cs.gamekit.graph.Graph;

import java.util.List;
import java.util.Optional;
import java.util.Set;

// TODO implement all methods and pass all tests
// MrX correct location is changed - possibly related to updating mrXLastRevealedPosition on correct rounds...
public class ScotlandYardModel implements ScotlandYardGame, Consumer<Move>, MoveVisitor {
	private List<Boolean> rounds;
	private int currentRound = ScotlandYardView.NOT_STARTED;
	private Graph<Integer, Transport> graph;
	private int mrXLastRevealedPosition = 0;
	private Colour currentPlayer;
	private Set<Move> currentMoves = new HashSet<Move>();
	private List<ScotlandYardPlayer> players = new ArrayList<ScotlandYardPlayer>();
	private List<Spectator> spectators = new ArrayList<Spectator>();
	private Set<Colour> winningPlayers = new HashSet<Colour>();

	public ScotlandYardModel(List<Boolean> rounds, Graph<Integer, Transport> graph, PlayerConfiguration mrX,
			PlayerConfiguration firstDetective, PlayerConfiguration... restOfTheDetectives) {
		this.rounds = requireNonNull(rounds);
		this.graph = requireNonNull(graph);
		if (rounds.isEmpty()) {
			throw new IllegalArgumentException("Empty rounds");
		}
		if (graph.isEmpty()) {
			throw new IllegalArgumentException("Empty graph");
		}
		if (mrX.colour != BLACK) {
			throw new IllegalArgumentException("MrX should be Black");
		}
		ArrayList<PlayerConfiguration> configurations = new ArrayList<>();
		for (PlayerConfiguration configuration : restOfTheDetectives) {
			configurations.add(requireNonNull(configuration));
		}
		configurations.add(0, firstDetective);
		configurations.add(0, mrX);
		currentPlayer = BLACK;
		Set<Integer> setLocation = new HashSet<>();
		Set<Colour> setColour = new HashSet<>();
		for (PlayerConfiguration configuration : configurations) {
			if (setLocation.contains(configuration.location)) {
				throw new IllegalArgumentException("Duplicate location");
			}
			if (setColour.contains(configuration.colour)) {
				throw new IllegalArgumentException("Duplicate colour");
			}
			if (checkDetectiveTickets(configuration) == false) {
				throw new IllegalArgumentException("Detective has wrong tickets");
			}
			if (checkMrXTickets(configuration) == false) {
				throw new IllegalArgumentException("Mr X has wrong tickets");
			}
			setLocation.add(configuration.location);
			setColour.add(configuration.colour);
			players.add(new ScotlandYardPlayer(configuration.player, configuration.colour, configuration.location,
					configuration.tickets));
		}

	}

	private boolean checkDetectiveTickets(PlayerConfiguration configuration) {
		if (configuration.colour != BLACK && (!(configuration.tickets.containsKey(Ticket.SECRET))
				|| !(configuration.tickets.containsKey(Ticket.DOUBLE))
				|| !(configuration.tickets.containsKey(Ticket.TAXI))
				|| !(configuration.tickets.containsKey(Ticket.UNDERGROUND))
				|| !(configuration.tickets.containsKey(Ticket.BUS)) || configuration.tickets.get(Ticket.DOUBLE) != 0
				|| configuration.tickets.get(Ticket.SECRET) != 0)) {
			return false;
		}
		return true;
	}

	private boolean checkMrXTickets(PlayerConfiguration configuration) {
		if (configuration.colour == BLACK && !(configuration.tickets.containsKey(Ticket.DOUBLE)
				&& configuration.tickets.containsKey(Ticket.SECRET) && configuration.tickets.containsKey(Ticket.TAXI)
				&& configuration.tickets.containsKey(Ticket.UNDERGROUND)
				&& configuration.tickets.containsKey(Ticket.BUS))) {
			return false;
		}
		return true;
	}

	@Override
	public void registerSpectator(Spectator spectator) {
		requireNonNull(spectator);
		if (this.spectators.contains(spectator)) {
			throw new IllegalArgumentException("Spectator already registered");
		}
		this.spectators.add(spectator);
	}

	@Override
	public void unregisterSpectator(Spectator spectator) {
		requireNonNull(spectator);
		if (this.spectators.remove(spectator))
			;
		else
			throw new IllegalArgumentException("Spectator not found");
	}

	@Override
	public void startRotate() {
		if (isGameOver())
			throw new IllegalStateException("Game Already Over");
		int i = 0;
		while (this.players.get(i).colour() != this.currentPlayer) {
			i++;
			if (this.players.size() == i) {
				throw new RuntimeException("Player not found");
			}
		}
		this.currentMoves = getValidMoves(this.currentPlayer);
		players.get(i).player().makeMove(requireNonNull(this), players.get(i).location(), this.currentMoves,
				requireNonNull(this));
	}

	@Override
	public Collection<Spectator> getSpectators() {
		return Collections.unmodifiableCollection(this.spectators);
	}

	@Override
	public List<Colour> getPlayers() {
		List<Colour> colours = new ArrayList<Colour>();
		for (ScotlandYardPlayer player : this.players) {
			colours.add(player.colour());
		}
		return Collections.unmodifiableList(colours);
	}

	@Override
	public Set<Colour> getWinningPlayers() {
		isGameOver();
		return Collections.unmodifiableSet(this.winningPlayers);
	}

	@Override
	public Optional<Integer> getPlayerLocation(Colour colour) {
		int i = 0;
		while (this.players.get(i).colour() != colour) {
			i++;
			if (this.players.size() == i) {
				return Optional.empty();
			}
		}
		if (colour == BLACK) {
				return Optional.of(this.mrXLastRevealedPosition);
		}
		return Optional.of(this.players.get(i).location());
	}

	@Override
	public Optional<Integer> getPlayerTickets(Colour colour, Ticket ticket) {
		int i = 0;
		while (this.players.get(i).colour() != colour) {
			i++;
			if (this.players.size() == i) {
				return Optional.empty();
			}
		}
		return Optional.of(this.players.get(i).tickets().get(ticket));
	}

	@Override
	public boolean isGameOver() {
		if ((this.currentPlayer == BLACK && this.rounds.size() == currentRound) || allDetectivesStuck()) {
			this.winningPlayers.add(BLACK);
			return true;
		}
		if ((mrXCornered()&&this.currentPlayer == BLACK) || mrXCaptured()) {
			for (ScotlandYardPlayer p : this.players) {
				if (p.isDetective())
					winningPlayers.add(p.colour());
			}
			return true;
		}
		return false;
	}

	@Override
	public Colour getCurrentPlayer() {
		return this.currentPlayer;
	}

	@Override
	public int getCurrentRound() {
		return this.currentRound;
	}

	@Override
	public List<Boolean> getRounds() {
		return Collections.unmodifiableList(this.rounds);
	}

	@Override
	public Graph<Integer, Transport> getGraph() {
		return new uk.ac.bris.cs.gamekit.graph.ImmutableGraph<Integer, Transport>(this.graph);
	}

	public boolean isRevealRound() {
		return this.rounds.get(currentRound-1);
	}

	public boolean isRevealRound(int r) {
		return this.rounds.get(r-1);
	}

	// announceMoveMade requires the first and second move to be passed as if they
	// were ticketmoves not just the doublemove
	@Override
	public void accept(Move t) {
		if (java.util.Objects.isNull(t)) {
			throw new NullPointerException("Move is Null");
		}
		if (!this.currentMoves.contains(t)) {
			throw new IllegalArgumentException("Invalid Move");
		}
		this.currentPlayer = getNextPlayer(this.currentPlayer);
		t.visit(this);	
		if (currentPlayer != BLACK && !isGameOver() ) {
			int i = getPlayerFromColour(currentPlayer);
			this.currentMoves = getValidMoves(this.currentPlayer);
			players.get(i).player().makeMove(requireNonNull(this), players.get(i).location(), this.currentMoves,
					requireNonNull(this));
		}
		else if (!isGameOver()) announceRotationComplete();		
	}

	private int returnPlayerLocation(Colour colour) {
		int i = getPlayerFromColour(colour);
		return this.players.get(i).location();
	}

	private Set<Move> getValidMoves(Colour c) {
		Set<Move> moves = new HashSet<Move>();
		List<uk.ac.bris.cs.gamekit.graph.Edge<Integer, Transport>> edges = new ArrayList<uk.ac.bris.cs.gamekit.graph.Edge<Integer, Transport>>();
		// double moves use a double move ticket then two transport tickets
		edges = (List<Edge<Integer, Transport>>) this.graph
				.getEdgesFrom(this.graph.getNode(this.returnPlayerLocation(c)));
		for (Edge<Integer, Transport> edge : edges) {
			if (this.currentPlayer == BLACK && requireNonNull(this.getPlayerTickets(BLACK, Ticket.SECRET)).get() > 0
					&& !spaceOccupiedByPlayer(edge.destination().value())) {
				TicketMove m = new TicketMove(c, Ticket.SECRET, edge.destination().value());
				moves.add(m);
				if (requireNonNull(this.getPlayerTickets(BLACK, Ticket.DOUBLE)).get() > 0
						&& (this.currentRound + 1) < this.rounds.size()) {
					addValidDoubleMoves(c, m, moves);
				}

			}
			if (this.getPlayerTickets(c, Ticket.fromTransport(edge.data())).get() > 0
					&& !spaceOccupiedByPlayer(edge.destination().value())) {
				TicketMove m = new TicketMove(c, Ticket.fromTransport(edge.data()), edge.destination().value());
				moves.add(m);
				if (this.currentPlayer == BLACK && requireNonNull(this.getPlayerTickets(BLACK, Ticket.DOUBLE)).get() > 0
						&& (this.currentRound + 1) < this.rounds.size()) {
					addValidDoubleMoves(c, m, moves);
				}
			}
		}
		if (c != BLACK && moves.isEmpty()) {
			moves.add(new PassMove(c));
		}
		return moves;
	}

	private void addValidDoubleMoves(Colour c, TicketMove firstMove, Set<Move> moves) {
		List<uk.ac.bris.cs.gamekit.graph.Edge<Integer, Transport>> edges = new ArrayList<uk.ac.bris.cs.gamekit.graph.Edge<Integer, Transport>>();
		edges = (List<Edge<Integer, Transport>>) this.graph.getEdgesFrom(this.graph.getNode(firstMove.destination()));
		for (Edge<Integer, Transport> edge : edges) {
			if (!spaceOccupiedByPlayer(edge.destination().value())) {
				TicketMove secondMove = new TicketMove(c, Ticket.fromTransport(edge.data()),
						edge.destination().value());
				if (firstMove.ticket() == secondMove.ticket()) {
					if (this.getPlayerTickets(c, Ticket.fromTransport(edge.data())).get() > 1) {
						moves.add(new DoubleMove(c, firstMove, secondMove));
					}
				} else {
					if (this.getPlayerTickets(c, Ticket.fromTransport(edge.data())).get() > 0) {
						moves.add(new DoubleMove(c, firstMove, secondMove));
					}
				}
				if (requireNonNull(this.getPlayerTickets(BLACK, Ticket.SECRET)).get() > 0) {
					moves.add(
							new DoubleMove(c, firstMove, new TicketMove(c, Ticket.SECRET, edge.destination().value())));
				}
			}
		}
	}

	private boolean spaceOccupiedByPlayer(int l) {
		for (ScotlandYardPlayer player : this.players) {
			if (player.location() == l && player.colour() != BLACK) {
				return true;
			}
		}
		return false;
	}

	private Colour getNextPlayer(Colour c) {
		int i = getPlayerFromColour(c);
		if ((i + 1) == this.players.size()) {
			return this.players.get(0).colour();
		}
		return this.players.get(i + 1).colour();
	}

	private int getPlayerFromColour(Colour c) {
		int i = 0;
		while (this.players.get(i).colour() != c) {
			i++;
			if (this.players.size() == i) {
				throw new RuntimeException("not found");
			}
		}
		return i;
	}

	@Override
	public void visit(TicketMove move) {
		this.players.get(getPlayerFromColour(move.colour())).location(move.destination());
		this.players.get(getPlayerFromColour(move.colour())).removeTicket(move.ticket());
		if (move.colour() != BLACK) {
			this.players.get(0).addTicket(move.ticket());
		}
		if (this.players.get(getPlayerFromColour(move.colour())).isMrX()) {
			this.currentRound++;
			//announceRoundStart();
			if (isRevealRound()) {
				this.mrXLastRevealedPosition = move.destination();
				announceRoundStart();
				announceMoveMade(move);
			} else {
				Move m = new TicketMove(move.colour(),move.ticket(),this.mrXLastRevealedPosition);
				announceRoundStart();
				announceMoveMade(m);
			}
		}
		else announceMoveMade(move);
		if (move.colour() != BLACK) {
			if (isGameOver()) announceGameOver();
		}		
	}

	@Override
	public void visit(DoubleMove move) {
		//spectatorMove is a DoubleMove with the correct information for the spectators
		DoubleMove spectatorMove = null;
		this.players.get(getPlayerFromColour(move.colour())).removeTicket(Ticket.DOUBLE);
		if (isRevealRound(this.currentRound+1) && isRevealRound(this.currentRound+2)) {
			spectatorMove = move;
			announceMoveMade(spectatorMove);
		}
		else if (isRevealRound(this.currentRound+1) && !isRevealRound(this.currentRound+2)) {
			spectatorMove = new DoubleMove(move.colour(),
					move.firstMove().ticket(),move.firstMove().destination(),
					move.secondMove().ticket(),move.firstMove().destination());
			announceMoveMade(spectatorMove);
		}
		else if (!isRevealRound(this.currentRound+1) && isRevealRound(this.currentRound+2)) {	
			spectatorMove = new DoubleMove(move.colour(),
					move.firstMove().ticket(),this.mrXLastRevealedPosition,
					move.secondMove().ticket(),move.secondMove().destination());
			announceMoveMade(spectatorMove);
		}
		else  {
			spectatorMove = new DoubleMove(move.colour(),
					move.firstMove().ticket(),this.mrXLastRevealedPosition,
					move.secondMove().ticket(),this.mrXLastRevealedPosition);
			announceMoveMade(spectatorMove);
		}
				
		visit(move.firstMove());
		visit(move.secondMove());	
	}

	public void visit(PassMove move) {
		announceMoveMade(move);
	}

	private boolean allDetectivesStuck() {
		for (ScotlandYardPlayer player : this.players) {
			// if any detective is not stuck return false
			if ((player.hasTickets(Ticket.TAXI) || player.hasTickets(Ticket.BUS)
					|| player.hasTickets(Ticket.UNDERGROUND)) && player.isDetective()) {
				return false;
			}
		}
		return true;
	}

	private boolean mrXCornered() {
		if (getValidMoves(BLACK).isEmpty())
			return true;
		return false;
	}

	private boolean mrXCaptured() {
		for (ScotlandYardPlayer player : this.players) {
			if (player.isDetective() && (player.location() == players.get(getPlayerFromColour(BLACK)).location()))
				return true;
		}
		return false;
	}

	private void announceMoveMade(Move move) {
		for (Spectator s : this.spectators) {
			s.onMoveMade(this, move);
		}
	}

	private void announceRoundStart() {
		for (Spectator s : this.spectators) {
			s.onRoundStarted(this, this.currentRound);
		}
	}

	private void announceRotationComplete() {
		for (Spectator s : this.spectators) {
			s.onRotationComplete(this);
		}
	}

	private void announceGameOver() {
		for (Spectator s : this.spectators) {
			s.onGameOver(this, getWinningPlayers());
		}
	}
}
